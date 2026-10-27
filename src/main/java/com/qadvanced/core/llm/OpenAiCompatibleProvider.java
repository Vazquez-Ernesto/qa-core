package com.qadvanced.core.llm;

import com.qadvanced.core.config.LlmProperties;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * Base class for every provider that speaks the OpenAI chat API (OpenRouter, Ollama, Groq...).
 * Subclasses only say which name and which settings they use.
 */
public abstract class OpenAiCompatibleProvider implements LlmProvider {

    private final ProviderName name;
    private final ChatLanguageModel model;

    protected OpenAiCompatibleProvider(ProviderName name, LlmProperties.OpenAiCompatible settings,
                                       LlmProperties.Llm llm) {
        this.name = name;
        this.model = settings != null && settings.configured()
                ? OpenAiChatModel.builder()
                        .apiKey(settings.apiKey())
                        .baseUrl(settings.baseUrl())
                        .modelName(settings.model())
                        .temperature((double) llm.temperature())
                        .maxTokens(llm.maxOutputTokens())
                        .timeout(llm.timeout())
                        .maxRetries(1)
                        .build()
                : null;
    }

    @Override
    public ProviderName name() {
        return name;
    }

    @Override
    public boolean isConfigured() {
        return model != null;
    }

    @Override
    public String generate(String systemPrompt, String userMessage) {
        return complete(systemPrompt, userMessage).text();
    }

    @Override
    public Completion complete(String systemPrompt, String userMessage) {
        ChatRequest request = ChatRequest.builder()
                .messages(SystemMessage.from(systemPrompt), UserMessage.from(userMessage))
                .build();
        ChatResponse response = model.chat(request);
        TokenUsage usage = response.tokenUsage();
        return new Completion(response.aiMessage().text(),
                usage == null || usage.inputTokenCount() == null ? 0 : usage.inputTokenCount(),
                usage == null || usage.outputTokenCount() == null ? 0 : usage.outputTokenCount());
    }
}
