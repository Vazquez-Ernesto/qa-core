package com.qadvanced.core.llm;

import com.qadvanced.core.config.LlmProperties;
import org.springframework.core.annotation.Order;

/** OpenRouter: last fallback, with many free models behind one key. */
@Order(3)
public class OpenRouterProvider extends OpenAiCompatibleProvider {

    public OpenRouterProvider(LlmProperties properties) {
        super(ProviderName.OPENROUTER, properties.openrouter(), properties.llm());
    }
}
