# Phase 2: incident analysis

`POST /api/v1/incidents/{id}/analysis` generates a synchronous analysis for an
existing incident. The request has no body. It returns 200 with content, provider,
model, generatedAt, latencyMs and nullable inputTokens/outputTokens/totalTokens.
An unknown UUID returns the existing 404 ProblemDetail; provider failures and
null/empty/blank content return a sanitized 502 ProblemDetail. No analysis is saved.

## Components and boundary

- `IncidentApi` declares the HTTP and OpenAPI contract. `IncidentController` only
  delegates to services; it has no mapper, repository or gateway dependency.
- `IncidentAnalysisService` loads the incident, creates an immutable title/description
  snapshot, invokes `IncidentAnalysisGateway`, and maps the result to the response DTO.
- `IncidentMapper` maps both existing incident responses and analysis results.
  Existing incident response mapping now takes place in `IncidentService`.
- `IncidentAnalysisInput` and `IncidentAnalysisResult` are application value objects;
  the latter carries plain text and metadata, with no framework/provider types.
- `OpenAiIncidentAnalysisGateway` isolates Spring AI, builds separate system/user
  messages, and reads content, actual model and usage from ChatResponse. If no model
  is reported, the configured model is returned. EmptyUsage becomes null counts;
  reported zero counts remain zero. Missing totals are not calculated by the adapter.
- `IncidentAnalysisException` wraps provider failures. Its distinct subtype
  `EmptyIncidentAnalysisException` represents empty content. The HTTP handler always
  emits a fixed public message, never the provider exception, cause, key or prompt.
- `AiConfiguration` supplies the model, configured model name, UTC Clock and a
  monotonic nanosecond supplier through constructor injection. `AiProperties`
  validates the application-owned model configuration.

```text
IncidentController -> IncidentAnalysisService -> IncidentRepository
                                 |
                                 v
                      IncidentAnalysisGateway <- OpenAiIncidentAnalysisGateway
                                                           -> Spring AI -> OpenAI
```

The analysis service uses `Propagation.NOT_SUPPORTED` to suspend any caller
transaction. Repository `findById` finishes its own read transaction before the
model is called. Open-in-view remains disabled. No DB transaction is active during
the network call; an outer caller transaction, if any, resumes on return. A caller
should avoid holding its own transaction while waiting for analysis, since suspended
transactions can still retain resources. Analysis uses a snapshot: later incident
updates are not reflected in an already-running request.

`generatedAt` is the application receipt time in UTC. `latencyMs` measures the
model call with a monotonic timer, not wall-clock subtraction. Tests inject fixed
clock/timer values and never sleep.

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

Service unit tests verify lookup, exact title/description forwarding, result mapping
and the unknown-incident path without invoking the gateway. Adapter tests verify
metadata, missing/partial usage, real zero counts, empty responses, error translation
and timing. MockMvc tests replace the gateway with a mock and cover 200/404/502,
JSON null usage, unchanged incident data and transaction suspension/restoration.
The OpenAPI test checks the new operation, response codes and DTO descriptions/examples.
All contexts use fictional credentials and a loopback URL. No test sends a real
OpenAI request or consumes tokens.

The adapter explicitly rejects null generation output as an empty response and
defensively handles null metadata with the configured model and null token counts.
Spring AI 2.0.1's ChatResponse constructor normalizes null metadata to a new empty
ChatResponseMetadata (`Objects.requireNonNullElse`), so ordinary construction cannot
produce a null getter result. The missing-metadata test exercises this constructor
path without reflection; a separate test covers null generation output.

There is no analysis persistence, structured output, retry/fallback policy, RAG,
tool calling or Ollama integration. This endpoint is synchronous and has no new
rate limiting or authentication. Repeated requests generate fresh analyses.
Live model availability, protocol behavior and production timeout policy remain
unvalidated; ADR-003 remains Proposed.
