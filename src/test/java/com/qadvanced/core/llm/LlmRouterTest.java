package com.qadvanced.core.llm;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmRouterTest {

    /** Short hedge delay so the timing tests run in milliseconds. */
    private static final Duration HEDGE = Duration.ofMillis(100);

    /** Hand-written fake: answers after an optional delay and records how it was used. */
    static class FakeProvider implements LlmProvider {
        private final ProviderName name;
        private final boolean configured;
        private final long delayMillis;
        private final Supplier<String> answer;
        final AtomicInteger calls = new AtomicInteger();
        final CountDownLatch interrupted = new CountDownLatch(1);

        FakeProvider(ProviderName name, boolean configured, long delayMillis, Supplier<String> answer) {
            this.name = name;
            this.configured = configured;
            this.delayMillis = delayMillis;
            this.answer = answer;
        }

        @Override public ProviderName name() { return name; }
        @Override public boolean isConfigured() { return configured; }

        @Override
        public String generate(String systemPrompt, String userMessage) {
            calls.incrementAndGet();
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    interrupted.countDown();
                    throw new RuntimeException("cancelled");
                }
            }
            return answer.get();
        }
    }

    private static FakeProvider answering(ProviderName name, String text) {
        return new FakeProvider(name, true, 0, () -> text);
    }

    private static FakeProvider answeringAfter(ProviderName name, long millis, String text) {
        return new FakeProvider(name, true, millis, () -> text);
    }

    private static FakeProvider failing(ProviderName name) {
        return new FakeProvider(name, true, 0, () -> { throw new RuntimeException("429 quota"); });
    }

    private static LlmRouter router(LlmProvider... providers) {
        return new LlmRouter(List.of(providers), HEDGE);
    }

    // ---- chain order and failures ----

    @Test
    void usesTheFirstProviderWhenItAnswersInTime() {
        FakeProvider ollama = answering(ProviderName.OLLAMA, "from ollama");

        LlmResult result = router(answering(ProviderName.GEMINI, "from gemini"), ollama).generate("s", "hi", null);

        assertThat(result).isEqualTo(new LlmResult("from gemini", ProviderName.GEMINI, false));
        assertThat(ollama.calls).hasValue(0);
    }

    @Test
    void failureStartsTheNextProviderWithoutWaitingForTheHedgeDelay() {
        LlmRouter slowHedge = new LlmRouter(
                List.of(failing(ProviderName.GEMINI), answering(ProviderName.OLLAMA, "from ollama")),
                Duration.ofSeconds(5));
        long started = System.nanoTime();

        LlmResult result = slowHedge.generate("s", "hi", null);

        assertThat(result).isEqualTo(new LlmResult("from ollama", ProviderName.OLLAMA, true));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void fallsBackWhenTheAnswerIsBlank() {
        LlmRouter router = router(answering(ProviderName.GEMINI, "   "), answering(ProviderName.OLLAMA, "ok"));

        assertThat(router.generate("s", "hi", null).provider()).isEqualTo(ProviderName.OLLAMA);
    }

    @Test
    void acceptsAnswersThatMentionErrors() {
        // Regression: the first prototype treated any text containing "Error" as a failure
        LlmRouter router = router(answering(ProviderName.GEMINI, "Error 500 when saving: Major"));

        assertThat(router.generate("s", "bug", null).text()).isEqualTo("Error 500 when saving: Major");
    }

    @Test
    void throwsWhenEveryProviderFails() {
        LlmRouter router = router(failing(ProviderName.GEMINI), failing(ProviderName.OLLAMA), failing(ProviderName.OPENROUTER));

        assertThatThrownBy(() -> router.generate("s", "hi", null))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("failed");
    }

    @Test
    void throwsWhenNoProviderIsConfigured() {
        assertThatThrownBy(() -> router().generate("s", "hi", null))
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("configured");
    }

    // ---- hedging: a slow provider does not block the answer ----

    @Test
    void slowFirstProviderGetsHedgedAndTheFastestAnswerWins() {
        FakeProvider gemini = answeringAfter(ProviderName.GEMINI, 3000, "late gemini");
        long started = System.nanoTime();

        LlmResult result = router(gemini, answering(ProviderName.OLLAMA, "fast ollama")).generate("s", "hi", null);

        assertThat(result).isEqualTo(new LlmResult("fast ollama", ProviderName.OLLAMA, true));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void slowAnswerIsKeptWhenItStillArrivesFirst() {
        // Why hedging and not a hard 5 s timeout: a 6 s answer from Gemini is a good answer
        LlmResult result = router(
                answeringAfter(ProviderName.GEMINI, 250, "gemini at 250 ms"),
                answeringAfter(ProviderName.OLLAMA, 3000, "ollama at 3 s")).generate("s", "hi", null);

        assertThat(result).isEqualTo(new LlmResult("gemini at 250 ms", ProviderName.GEMINI, false));
    }

    @Test
    void cancelsTheProvidersThatLost() throws InterruptedException {
        FakeProvider gemini = answeringAfter(ProviderName.GEMINI, 5000, "too late");

        router(gemini, answering(ProviderName.OLLAMA, "winner")).generate("s", "hi", null);

        assertThat(gemini.interrupted.await(2, TimeUnit.SECONDS)).as("slow provider was interrupted").isTrue();
    }

    @Test
    void startsProvidersOneHedgeDelayApart() {
        FakeProvider gemini = answeringAfter(ProviderName.GEMINI, 2000, "g");
        FakeProvider ollama = answeringAfter(ProviderName.OLLAMA, 2000, "o");
        FakeProvider openrouter = answeringAfter(ProviderName.OPENROUTER, 50, "openrouter");

        LlmResult result = router(gemini, ollama, openrouter).generate("s", "hi", null);

        assertThat(result.provider()).isEqualTo(ProviderName.OPENROUTER);
        assertThat(List.of(gemini.calls.get(), ollama.calls.get(), openrouter.calls.get())).containsExactly(1, 1, 1);
    }

    // ---- preference and configuration ----

    @Test
    void triesThePreferredProviderFirstWithoutChangingTheDefaultOrder() {
        LlmRouter router = router(answering(ProviderName.GEMINI, "from gemini"), answering(ProviderName.OLLAMA, "from ollama"));

        assertThat(router.generate("s", "hi", ProviderName.OLLAMA).provider()).isEqualTo(ProviderName.OLLAMA);
        // The next request without preference uses the default order again (no global state)
        assertThat(router.generate("s", "hi", null).provider()).isEqualTo(ProviderName.GEMINI);
    }

    @Test
    void skipsProvidersWithoutApiKey() {
        FakeProvider unconfigured = new FakeProvider(ProviderName.GEMINI, false, 0, () -> "never");
        LlmRouter router = router(unconfigured, answering(ProviderName.OPENROUTER, "ok"));

        LlmResult result = router.generate("s", "hi", ProviderName.GEMINI);

        assertThat(result.provider()).isEqualTo(ProviderName.OPENROUTER);
        assertThat(result.fallback()).isFalse();
        assertThat(unconfigured.calls).hasValue(0);
        assertThat(router.configuredProviders()).containsExactly(ProviderName.OPENROUTER);
    }

    @Test
    void logsTheRootCauseOfWrappedErrors() {
        RuntimeException wrapped = new RuntimeException("Failed to execute HTTP request.",
                new java.io.IOException("io", new java.net.SocketTimeoutException("Read timed out")));

        assertThat(LlmRouter.describe(wrapped))
                .isEqualTo("Failed to execute HTTP request. (cause: SocketTimeoutException: Read timed out)");
        assertThat(LlmRouter.describe(new RuntimeException("429 quota"))).isEqualTo("429 quota");
    }

    @Test
    void passesOnTheTokensOfTheProviderThatAnswered() {
        LlmProvider counting = new LlmProvider() {
            @Override public ProviderName name() { return ProviderName.GEMINI; }
            @Override public boolean isConfigured() { return true; }
            @Override public String generate(String systemPrompt, String userMessage) { return "answer"; }
            @Override public Completion complete(String systemPrompt, String userMessage) {
                return new Completion("answer", 120, 45);
            }
        };

        LlmResult result = new LlmRouter(List.of(counting), Duration.ofSeconds(5)).generate("system", "user", null);

        assertThat(result).isEqualTo(new LlmResult("answer", ProviderName.GEMINI, false, 120, 45));
    }
}
