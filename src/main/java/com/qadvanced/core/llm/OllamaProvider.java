package com.qadvanced.core.llm;

import com.qadvanced.core.config.LlmProperties;
import org.springframework.core.annotation.Order;

/**
 * Ollama Cloud (https://ollama.com/v1) in production, or a local Ollama
 * (http://localhost:11434/v1) during development. Both speak the OpenAI API.
 */
@Order(2)
public class OllamaProvider extends OpenAiCompatibleProvider {

    public OllamaProvider(LlmProperties properties) {
        super(ProviderName.OLLAMA, properties.ollama(), properties.llm());
    }
}
