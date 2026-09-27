# Phase 2: incident analysis

`POST /api/v1/incidents/{id}/analysis` generates a synchronous analysis for an
existing incident. The request has no body. It returns 200 with content, provider,
model, promptVersion, generatedAt, latencyMs and nullable inputTokens/outputTokens/totalTokens.
An unknown UUID returns the existing 404 ProblemDetail; provider failures and
null/empty/blank content return a sanitized 502 ProblemDetail. Transport timeouts
return 504 with a fixed safe message. No analysis is saved.

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
- `IncidentAnalysisPrompt` loads a versioned system/user template pair at startup.
  It renders title/description only in the user template, preserving literal data.
- `AiConfiguration` supplies the model, properties, prompt, UTC Clock and a
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

## Versioned prompt contract

`incident-analysis-v1` maps explicitly to these UTF-8 classpath resources:

- `prompts/incident-analysis/v1/system.st`
- `prompts/incident-analysis/v1/user.st`

The system message asks for at most three likely causes and four concrete
investigation steps, uncertainty and evidence, with short bullets and no unsupported
root-cause claim. Incident content is untrusted data and cannot be interpolated into
system instructions. The user template binds only title and description; values are
not recursively rendered as templates. Role separation reduces instruction confusion
but does not guarantee that a model will resist every prompt injection.

The version is an immutable behavioral identifier. Future prompt changes must add a
new directory and explicitly register a new identifier, rather than rewriting v1.
`promptVersion` is returned in the provider-neutral result and REST DTO.

| Application property | Environment variable | Default | Validation |
|---|---|---|---|
| `app.ai.prompt-version` | `AI_PROMPT_VERSION` | `incident-analysis-v1` | Nonblank, registered version |
| `app.ai.max-output-tokens` | `OPENAI_MAX_OUTPUT_TOKENS` | `450` | Integer, 1–16384 |

The upper bound is an application guardrail; provider/model capabilities can be
more restrictive. OpenAI-specific `maxCompletionTokens` is applied to each request
inside the infrastructure adapter. The adapter calls ChatModel directly with a
Prompt, avoiding ChatClient's automatic tool-calling advisor and preserving empty-output
and partial-usage handling. No temperature, retries or fallback are added.
For reasoning models, the completion budget may include reasoning tokens and can
truncate visible output. The current contract does not expose the finish reason.

## Transport timeout

`app.ai.timeout` is a `Duration`, bound from `AI_TIMEOUT` with default `20s`.
It is required, at least 1 ms and at most 2147483647 ms (OkHttp's millisecond range).
Blank, zero, negative and out-of-range values are rejected at startup.

Dependency sources inspected: Spring AI 2.0.1 and OpenAI Java core 4.49.0.
The implementation uses the supported configuration path, without a custom executor:

```text
app.ai.timeout -> spring.ai.openai.timeout
  -> OpenAiChatAutoConfiguration -> OpenAiSetup
  -> ClientOptions.timeout + SpringAiOpenAiHttpClient.Builder.timeout(Duration)
  -> OkHttpClient.Builder.callTimeout(Duration)
```

The SDK timeout includes the entire HTTP call, including request/response body I/O;
read/write timeouts inherit that budget. OkHttp handles cancellation at transport
level. No CompletableFuture race leaves a detached request running in the background.
The existing SDK `max-retries=0` is unchanged; no retry/fallback mechanism is added.

SpringAiOpenAiHttpClient wraps I/O failures in `OpenAIIoException`. The adapter
walks the cause chain for `SocketTimeoutException` or OkHttp's whole-call
`InterruptedIOException("timeout")` and emits `IncidentAnalysisTimeoutException`.
An ordinary connection failure or interruption remains a generic 502 error.
The REST handler returns a fixed 504 detail, never a cause, URL, key or prompt.
All SDK/transport exception inspection remains in infrastructure.

Context tests hook the supported `OpenAiHttpClientBuilderCustomizer` seam after
OpenAiSetup configures each sync/async builder. They build and inspect the actual
OkHttp clients' `callTimeoutMillis()` without executing any request. Adapter tests
inject the exact transport exception shapes; MockMvc verifies safe 504 responses.
No timeout test sleeps or waits for an actual deadline. These tests establish wiring
and exception translation, not a measured live deadline. Client cancellation also
cannot guarantee cancellation of provider-side computation or billing.

## Verification and remaining limits

Service unit tests verify lookup, exact title/description forwarding, result mapping
and the unknown-incident path without invoking the gateway. Adapter tests verify
metadata, missing/partial usage, real zero counts, empty responses, error translation
and timing. Tests load the actual versioned resources, verify separate roles and
literal substitutions, capture real OpenAiChatOptions with the configured limit,
and verify configuration defaults, overrides and invalid values. MockMvc tests replace the gateway with a mock and cover 200/404/502/504,
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
Two user-reported live smoke tests on 2026-09-27 succeeded with gpt-6-luna
(see ADR-003). The second used incident-analysis-v1, took 5816 ms and produced
383 output tokens within the 450-token budget; the response was complete.
No repeat live request was made for timeout implementation. Timeout wiring and
error translation are tested offline; structured-output conversion remains
outstanding, so ADR-003 remains Proposed.
