# qa-core

> Shared core for QAdvanced: an LLM model router with hedged fallback across Gemini, Ollama and OpenRouter.

This is the first library extracted from [`qa-flows`](https://github.com/Vazquez-Ernesto/qa-flows). It owns the
**model router** — the piece that asks several LLM providers in order and returns the first good answer —
decoupled from qa-flows' own configuration so it can be reused by other QAdvanced products
(`qa-flows`, `ernesto-agents`, `scenario-lab`).

## Coordinates

| | |
|---|---|
| groupId | `com.qadvanced` |
| artifactId | `qa-core` |
| version | `0.1.0` |
| Java | 21 |

## What it does

```
                 ┌─ GeminiProvider      @Order(1)
LlmRouter ───────┼─ OllamaProvider      @Order(2)
 (hedged chain)  └─ OpenRouterProvider  @Order(3)
```

- **Hedging**: if a provider has not answered after `qacore.llm.hedge-delay` (default 5 s), the next one starts
  *in parallel* instead of cancelling the slow one. The first good answer wins; the losers are cancelled.
- **Fail-fast on error**: a provider that throws starts the next one immediately (no hedge wait).
- **Unconfigured providers are skipped**: a missing API key disables a provider without crashing startup.
- **Token accounting**: each answer carries the input/output tokens the provider billed.

## Configuration

Spring Boot binds these properties from the `qacore.*` prefix (environment-variable names are unchanged):

| Property | Env var | Default |
|---|---|---|
| `qacore.gemini.api-key` | `GEMINI_API_KEY` | — |
| `qacore.gemini.model` | `GEMINI_MODEL` | `gemini-flash-lite-latest` |
| `qacore.gemini.thinking-level` | `GEMINI_THINKING_LEVEL` | `low` |
| `qacore.ollama.api-key` | `OLLAMA_API_KEY` | — |
| `qacore.ollama.model` | `OLLAMA_MODEL` | `gpt-oss:120b` |
| `qacore.ollama.base-url` | `OLLAMA_BASE_URL` | `https://ollama.com/v1` |
| `qacore.openrouter.api-key` | `OPENROUTER_API_KEY` | — |
| `qacore.openrouter.model` | `OPENROUTER_MODEL` | `openrouter/free` |
| `qacore.openrouter.base-url` | `OPENROUTER_BASE_URL` | `https://openrouter.ai/api/v1` |
| `qacore.llm.timeout` | `LLM_TIMEOUT` | `40s` |
| `qacore.llm.hedge-delay` | `LLM_HEDGE_DELAY` | `5s` |
| `qacore.llm.temperature` | — | `0.2` |
| `qacore.llm.max-output-tokens` | — | `4096` |

## Consuming it

The library registers itself through Spring Boot auto-configuration
(`META-INF/spring/...AutoConfiguration.imports`), so a consumer app needs **zero wiring**:

1. Add the dependency (JitPack example):

   ```xml
   <repositories>
       <repository>
           <id>jitpack.io</id>
           <url>https://jitpack.io</url>
       </repository>
   </repositories>

   <dependency>
       <groupId>com.github.Vazquez-Ernesto</groupId>
       <artifactId>qa-core</artifactId>
       <version>0.1.0</version>
   </dependency>
   ```

2. Set the `qacore.*` properties (or the env vars above).
3. Inject `LlmRouter` and call `generate(systemPrompt, userMessage, preferred)`.

## Build & test

Requires JDK 21+ and Maven 3.9+.

```bash
mvn test          # unit tests — no network, no API keys, no tokens spent
mvn install       # install into the local ~/.m2 repository
```

## Publishing (JitPack)

Tag a release and JitPack builds the artifact from the GitHub repo:

```bash
git tag 0.1.0
git push origin 0.1.0
```

Then reference `com.github.Vazquez-Ernesto:qa-core:0.1.0` as shown above.

## License

UNLICENSED (private). Decide open-core vs closed source before making the repo public.
