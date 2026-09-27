# Phase 2: AI infrastructure

This step wires an OpenAI adapter without connecting it to IncidentController or
IncidentService. There are no startup runners or real API calls in tests.

## Components and boundary

- `IncidentAnalysisGateway`: application-owned API for a future analysis service;
  accepts an incident snapshot and returns plain text, with no Spring AI types.
- `IncidentAnalysisInput`: immutable title/description snapshot. It does not expose
  the JPA entity or require loading persistence in adapter tests.
- `OpenAiIncidentAnalysisGateway`: constructor-injected adapter around ChatClient.
  Maps incident content into a user message and supplies a separate system message.
  Only an explicit `analyze` invocation calls the model.
- `AiConfiguration`: wires the adapter to the starter's OpenAiChatModel and
  registers configuration properties. No business service depends on this class.
- `AiProperties`: validates the application-owned `app.ai.model` setting.
  YAML maps `OPENAI_MODEL` (default `gpt-6-luna`) into this setting and forwards it
  to `spring.ai.openai.chat.model`. Secrets are not part of this record.

Future dependency direction:

```text
analysis service -> IncidentAnalysisGateway <- OpenAiIncidentAnalysisGateway
                                                   -> Spring AI -> OpenAI
```

The future service can substitute a fake gateway without any provider imports.
The current adapter returns unstructured text. Provider error translation and
response validation are deliberately deferred with orchestration; callers should
not yet depend on a stable error contract.

## Dependency and configuration decisions

Java 21 and Spring Boot 4.1.1 are unchanged. The official Spring AI BOM 2.0.1
manages `spring-ai-starter-model-openai`; no milestone/snapshot repositories or
manual Spring Framework overrides are used.
[Spring AI's compatibility documentation](https://docs.spring.io/spring-ai/reference/getting-started.html)
lists Boot 4.0.x and 4.1.x support for Spring AI 2.0.x.

The AI starter excludes both transitive Swagger annotation artifacts so that
springdoc 3.1.1 supplies its matching `swagger-annotations-jakarta` 2.2.55.
Without this, AI brings Jakarta 2.2.38 and the SDK brings non-Jakarta 2.2.31
with duplicate classes; `/v3/api-docs` fails with `Schema.$dynamicRef()` missing.
The existing OpenAPI test covers this compatibility regression.

`OPENAI_API_KEY` is required at application startup; there is no production
fallback key. `.env.example` is illustrative and is not automatically loaded.
Only chat is enabled; embedding, image, moderation, speech and transcription
auto-configurations are disabled. SDK retries are explicitly set to zero.
No sampling parameters are imposed on the selected model.

## Verification and remaining limits

Unit tests use a mocked ChatModel to verify deferred invocation, incident message
mapping (including literal JSON braces), and plain-text responses. Context tests
construct the real adapter/model with a fictional key and a loopback URL, verify
the default and overridden model, and reject blank model configuration.
All existing Spring Boot tests receive test-only credentials and a loopback URL.
No test invokes the live provider or consumes tokens.

The default model name is the requested configuration value, not evidence of
availability for a particular OpenAI account. A successful context startup does
not establish provider protocol compatibility. Real calls, structured output,
timeouts/error handling and usage metadata remain future spike work; ADR-003
therefore stays Proposed. There is no new persistence, retry policy, RAG,
tool calling, Ollama integration, or REST endpoint.
