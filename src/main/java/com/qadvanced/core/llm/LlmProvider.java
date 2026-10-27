package com.qadvanced.core.llm;

/**
 * One LLM backend (Gemini, OpenRouter...). {@link LlmRouter} chains them with fallback,
 * so adding a provider means adding one class that implements this interface.
 */
public interface LlmProvider {

    ProviderName name();

    /** False when the API key is missing; unconfigured providers are skipped. */
    boolean isConfigured();

    /**
     * @return the model's text answer; may be null or blank when the model returned nothing
     * @throws RuntimeException on network errors, timeouts or API errors
     */
    String generate(String systemPrompt, String userMessage);

    /** Like {@link #generate} but with the tokens used; providers that know them override this. */
    default Completion complete(String systemPrompt, String userMessage) {
        return new Completion(generate(systemPrompt, userMessage), 0, 0);
    }
}
