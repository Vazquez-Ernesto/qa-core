package com.qadvanced.core.llm;

import com.qadvanced.core.config.LlmProperties;
import com.google.genai.types.GenerateContentConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiProviderTest {

    private static final LlmProperties.Llm LLM = new LlmProperties.Llm(Duration.ofSeconds(25), Duration.ofSeconds(5), 0.3f, 2048);

    @Test
    void sendsTheSystemPromptAsSystemInstruction() {
        GenerateContentConfig config = GeminiProvider.config("# Agent", LLM, "low");

        assertThat(config.systemInstruction().orElseThrow().parts().orElseThrow().getFirst().text())
                .contains("# Agent");
        assertThat(config.maxOutputTokens()).contains(2048);
    }

    @Test
    void appliesTheThinkingLevelWhenConfigured() {
        GenerateContentConfig config = GeminiProvider.config("# Agent", LLM, " low ");

        assertThat(config.thinkingConfig().orElseThrow().thinkingLevel().orElseThrow().toString())
                .isEqualToIgnoringCase("low");
    }

    @Test
    void omitsThinkingConfigForModelsWithoutLevels() {
        assertThat(GeminiProvider.config("# Agent", LLM, "").thinkingConfig()).isEmpty();
        assertThat(GeminiProvider.config("# Agent", LLM, null).thinkingConfig()).isEmpty();
    }
}
