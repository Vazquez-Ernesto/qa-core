package com.qadvanced.core.llm;

/**
 * What one provider call returned.
 *
 * @param inputTokens  prompt tokens the provider billed; 0 when it did not say
 * @param outputTokens answer tokens the provider billed (including the model's thinking); 0 when it did not say
 */
public record Completion(String text, long inputTokens, long outputTokens) {}
