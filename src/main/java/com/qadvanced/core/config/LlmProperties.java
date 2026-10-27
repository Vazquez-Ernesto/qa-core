package com.qadvanced.core.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of every {@code qacore.*} property the LLM router reads.
 * Spring binds the values through the record constructor.
 *
 * <p>This is the library's own slice of configuration — only what the router needs.
 * The consumer app keeps its own properties class for the rest (Jira, security, pricing...).
 */
@ConfigurationProperties(prefix = "qacore")
public record LlmProperties(
        Gemini gemini,
        OpenAiCompatible ollama,
        OpenAiCompatible openrouter,
        Llm llm
) {

    /** @param thinkingLevel "low"/"high" for Gemini 3.x models; empty = model default */
    public record Gemini(String apiKey, String model, String thinkingLevel) {
        public boolean configured() {
            return hasText(apiKey);
        }
    }

    /** Any OpenAI-compatible API: Ollama (cloud or local), OpenRouter, Groq... */
    public record OpenAiCompatible(String apiKey, String model, String baseUrl) {
        public boolean configured() {
            return hasText(apiKey);
        }
    }

    /**
     * @param timeout    per provider call
     * @param hedgeDelay without an answer after this long, the next provider starts in parallel
     */
    public record Llm(Duration timeout, Duration hedgeDelay, float temperature, int maxOutputTokens) {}

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
