package com.qadvanced.core.llm;

/**
 * @param fallback     true when the answer did not come from the first provider in the chain
 * @param inputTokens  prompt tokens of the provider that answered; 0 when unknown
 * @param outputTokens answer tokens of the provider that answered; 0 when unknown
 */
public record LlmResult(String text, ProviderName provider, boolean fallback, long inputTokens, long outputTokens) {

    public LlmResult(String text, ProviderName provider, boolean fallback) {
        this(text, provider, fallback, 0, 0);
    }
}
