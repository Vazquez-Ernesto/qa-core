package com.qadvanced.core.config;

import com.qadvanced.core.llm.GeminiProvider;
import com.qadvanced.core.llm.LlmRouter;
import com.qadvanced.core.llm.OllamaProvider;
import com.qadvanced.core.llm.OpenRouterProvider;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Registers the LLM router and its providers when qa-core is on the classpath.
 *
 * <p>Registered through
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports},
 * so a consumer app only adds the dependency and sets the {@code qacore.*} properties —
 * no component scanning, no {@code @Import} on the consumer side.
 *
 * <p>Spring injects {@code List<LlmProvider>} into {@link LlmRouter} sorted by
 * {@code @Order}, which gives the default chain Gemini (1) → Ollama (2) → OpenRouter (3).
 */
@AutoConfiguration
@EnableConfigurationProperties(LlmProperties.class)
@Import({LlmRouter.class, GeminiProvider.class, OllamaProvider.class, OpenRouterProvider.class})
public class LlmAutoConfiguration {
}
