package com.qadvanced.core.llm;

import com.qadvanced.core.config.LlmProperties;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import org.springframework.core.annotation.Order;

@Order(1)
public class GeminiProvider implements LlmProvider {

    private final Client client;
    private final String model;
    private final String thinkingLevel;
    private final LlmProperties.Llm llm;

    public GeminiProvider(LlmProperties properties) {
        LlmProperties.Gemini gemini = properties.gemini();
        this.model = gemini.model();
        this.thinkingLevel = gemini.thinkingLevel();
        this.llm = properties.llm();
        // Without a key the client stays null and the router skips this provider,
        // so the app still starts (it used to crash on startup).
        this.client = gemini.configured()
                ? Client.builder()
                        .apiKey(gemini.apiKey())
                        .httpOptions(HttpOptions.builder().timeout((int) llm.timeout().toMillis()).build())
                        .build()
                : null;
    }

    @Override
    public ProviderName name() {
        return ProviderName.GEMINI;
    }

    @Override
    public boolean isConfigured() {
        return client != null;
    }

    @Override
    public String generate(String systemPrompt, String userMessage) {
        return complete(systemPrompt, userMessage).text();
    }

    @Override
    public Completion complete(String systemPrompt, String userMessage) {
        GenerateContentResponse response =
                client.models.generateContent(model, userMessage, config(systemPrompt, llm, thinkingLevel));
        long input = response.usageMetadata().flatMap(GenerateContentResponseUsageMetadata::promptTokenCount).orElse(0);
        // Gemini bills the thinking tokens as output but reports them apart
        long output = response.usageMetadata()
                .map(usage -> usage.candidatesTokenCount().orElse(0) + usage.thoughtsTokenCount().orElse(0))
                .orElse(0);
        return new Completion(response.text(), input, output);
    }

    /**
     * The system prompt goes in its own field instead of being glued to the user text,
     * which makes it harder for a user message to override the agent's instructions.
     * A low thinking level keeps Gemini 3.x answers in seconds instead of half a minute.
     */
    static GenerateContentConfig config(String systemPrompt, LlmProperties.Llm llm, String thinkingLevel) {
        GenerateContentConfig.Builder config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
                .temperature(llm.temperature())
                .maxOutputTokens(llm.maxOutputTokens());
        if (thinkingLevel != null && !thinkingLevel.isBlank()) {
            config.thinkingConfig(ThinkingConfig.builder().thinkingLevel(thinkingLevel.strip()).build());
        }
        return config.build();
    }
}
