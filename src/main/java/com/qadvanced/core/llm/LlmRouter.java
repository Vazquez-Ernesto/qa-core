package com.qadvanced.core.llm;

import com.qadvanced.core.config.LlmProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Asks the configured providers in order and returns the first good answer.
 *
 * <p>Hedged requests: if a provider has not answered after {@code qacore.llm.hedge-delay}
 * (5 s), the next one is started IN PARALLEL instead of cancelling the slow one, so an answer
 * that arrives at 6 s is not thrown away. A provider that fails starts the next one at once.
 * When one answers, the others are cancelled.
 *
 * <p>The caller may prefer a provider for a single request; that never changes global state,
 * so one visitor cannot switch the provider for everyone else.
 */
public class LlmRouter {

    private static final Logger log = LoggerFactory.getLogger(LlmRouter.class);
    /** Safety net in case an SDK ignores its own timeout. */
    private static final Duration MAX_WAIT = Duration.ofSeconds(120);

    private final List<LlmProvider> providers;
    private final Duration hedgeDelay;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /** Spring injects every LlmProvider bean, sorted by its @Order. */
    @Autowired
    public LlmRouter(List<LlmProvider> providers, LlmProperties properties) {
        this(providers, properties.llm().hedgeDelay());
    }

    LlmRouter(List<LlmProvider> providers, Duration hedgeDelay) {
        this.providers = List.copyOf(providers);
        this.hedgeDelay = hedgeDelay;
    }

    private record Attempt(LlmProvider provider, Completion completion, RuntimeException error) {
        boolean succeeded() {
            // Failure is decided by exceptions and empty answers, never by the text content:
            // a legitimate answer may well contain the word "Error".
            return error == null && completion != null && completion.text() != null && !completion.text().isBlank();
        }
    }

    public LlmResult generate(String systemPrompt, String userMessage, ProviderName preferred) {
        List<LlmProvider> chain = chainFor(preferred);
        if (chain.isEmpty()) {
            throw new LlmUnavailableException("No LLM provider is configured");
        }

        BlockingQueue<Attempt> finished = new LinkedBlockingQueue<>();
        List<Future<?>> running = new ArrayList<>();
        int launched = 0;
        int done = 0;
        try {
            launched = launch(chain, launched, systemPrompt, userMessage, finished, running);
            long nextHedgeAt = System.nanoTime() + hedgeDelay.toNanos();

            while (done < launched || launched < chain.size()) {
                Attempt attempt;
                if (launched < chain.size()) {
                    long wait = nextHedgeAt - System.nanoTime();
                    attempt = wait > 0 ? finished.poll(wait, TimeUnit.NANOSECONDS) : null;
                    if (attempt == null) {
                        log.info("No answer after {} ms, also asking {}",
                                hedgeDelay.toMillis(), chain.get(launched).name());
                        launched = launch(chain, launched, systemPrompt, userMessage, finished, running);
                        nextHedgeAt = System.nanoTime() + hedgeDelay.toNanos();
                        continue;
                    }
                } else {
                    attempt = finished.poll(MAX_WAIT.toMillis(), TimeUnit.MILLISECONDS);
                    if (attempt == null) {
                        break;
                    }
                }

                done++;
                if (attempt.succeeded()) {
                    Completion completion = attempt.completion();
                    return new LlmResult(completion.text().strip(), attempt.provider().name(),
                            attempt.provider() != chain.getFirst(), completion.inputTokens(), completion.outputTokens());
                }
                log.warn("{} failed: {}", attempt.provider().name(),
                        attempt.error() == null ? "empty answer" : describe(attempt.error()));
                if (launched < chain.size()) {
                    // A failure does not wait for the hedge delay: the next provider starts now
                    launched = launch(chain, launched, systemPrompt, userMessage, finished, running);
                    nextHedgeAt = System.nanoTime() + hedgeDelay.toNanos();
                }
            }
            throw new LlmUnavailableException("All LLM providers failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmUnavailableException("Interrupted while waiting for the LLM providers");
        } finally {
            running.forEach(future -> future.cancel(true));
        }
    }

    private int launch(List<LlmProvider> chain, int index, String systemPrompt, String userMessage,
                       BlockingQueue<Attempt> finished, List<Future<?>> running) {
        LlmProvider provider = chain.get(index);
        running.add(executor.submit(() -> {
            try {
                finished.add(new Attempt(provider, provider.complete(systemPrompt, userMessage), null));
            } catch (RuntimeException e) {
                finished.add(new Attempt(provider, null, e));
            }
        }));
        return index + 1;
    }

    /**
     * SDKs wrap the real problem ("Failed to execute HTTP request"); the root cause
     * (e.g. SocketTimeoutException: Read timed out) is what tells timeouts from quota errors.
     */
    static String describe(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root == error
                ? error.getMessage()
                : error.getMessage() + " (cause: " + root.getClass().getSimpleName() + ": " + root.getMessage() + ")";
    }

    public List<ProviderName> configuredProviders() {
        return chainFor(null).stream().map(LlmProvider::name).toList();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private List<LlmProvider> chainFor(ProviderName preferred) {
        return providers.stream()
                .filter(LlmProvider::isConfigured)
                // false sorts before true, so the preferred provider goes first; the sort is stable
                .sorted(Comparator.comparing(provider -> provider.name() != preferred))
                .toList();
    }
}
