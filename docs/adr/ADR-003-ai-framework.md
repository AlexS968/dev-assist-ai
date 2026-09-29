# ADR-003: Select the AI application framework

- Status: Accepted
- Date: 2026-09-20

## Context

Later phases require:

- LLM integration;
- structured output;
- prompt templates;
- embeddings;
- retrieval-augmented generation;
- tool calling;
- evaluation metadata;
- AI observability;
- potentially MCP integration.

The application should avoid provider-specific code in its core business logic.
At the same time, introducing a large custom abstraction or using multiple AI
frameworks would add unnecessary complexity.

The main candidates are:

- Spring AI;
- LangChain4J;
- direct provider SDK integration.

## Decision

Use Spring AI as the primary AI application framework. The integration spike and
reported live structured-output tests satisfy the acceptance criteria.

Keep application-specific boundaries such as `IncidentAnalysisGateway` around
probabilistic operations. Business code should depend on those boundaries rather
than provider-specific classes.

Use one AI framework. Do not combine Spring AI and LangChain4J unless a concrete
missing capability is demonstrated.

## Alternatives considered

### LangChain4J

LangChain4J provides broad Java-focused AI functionality and is a credible
alternative. It is not currently preferred because this project is centered on
Spring Boot and Spring AI offers direct integration with Spring configuration,
observability, tools, vector stores, and MCP.

### Direct provider SDK

A provider SDK gives maximum control and early access to provider features.
However, it also introduces provider-specific request models, error handling,
tool schemas, and observability concerns throughout the application.

### Custom AI abstraction layer

A large provider-neutral abstraction would duplicate framework functionality and
would be speculative before the application has experience with more than one
provider.

## Expected consequences

### Positive

- idiomatic integration with Spring Boot;
- consistent configuration and dependency management;
- built-in support for structured output, tools, vector stores, and observations;
- reduced amount of custom infrastructure code.

### Negative

- some advanced provider features may not be exposed immediately;
- framework APIs may evolve;
- switching frameworks would still require adaptation;
- provider portability is not guaranteed merely by using a common interface.

## Risks and mitigations

### Framework lock-in

Keep domain models and application orchestration independent from Spring AI
types where practical.

### Provider-specific behavior

Test structured output, tool calling, token metadata, and timeout behavior
against the selected provider instead of assuming uniform behavior.

### Rapid API evolution

Pin stable versions and isolate framework usage inside the AI infrastructure
boundary.

## Original acceptance criteria

The integration spike was required to demonstrate:

- one successful model call;
- structured output conversion;
- timeout and provider error handling;
- replacement with a fake implementation in automated tests;
- access to response usage metadata.

## Integration spike evidence — 2026-09-27

The project owner reported a successful live smoke test:

| Observation | Value |
|---|---|
| Endpoint | `POST /api/v1/incidents/{id}/analysis` |
| HTTP status | 200 |
| Provider | openai |
| Model | gpt-6-luna |
| Input tokens | 71 |
| Output tokens | 591 |
| Total tokens | 662 |
| Latency | 7817 ms |
| Independent confirmation | Call confirmed in OpenAI Usage |

This records the supplied result; no repeat live request was performed for the
prompt-versioning step. No credentials, full prompt or full AI response are recorded.
The call preceded versioned templates and the new output-token limit.

Observed limitation: the first answer was longer than desired. This motivates
`incident-analysis-v1`, concise response instructions and a configurable completion
budget of 450 tokens by default. The second smoke test below validates the revised
prompt and limit for one observed response.

### Second live smoke test — 2026-09-27

The project owner supplied these additional observations:

| Observation | Value |
|---|---|
| HTTP status | 200 |
| Model | gpt-6-luna |
| Prompt version | incident-analysis-v1 |
| Latency | 5816 ms |
| Input tokens | 141 |
| Output tokens | 383 |
| Output limit | 450, respected |
| Completion | Answer complete, not truncated |

No additional live call was made for timeout implementation. No total-token value
is asserted for the second test because it was not supplied. No credentials,
full prompt or full response are included.

### First live Ollama application smoke test — 2026-09-27

The project owner reported the first successful Ollama call through the application:

| Observation | Value |
|---|---|
| Endpoint | `POST /api/v1/incidents/{id}/analysis` |
| HTTP status | 200 |
| Provider | ollama |
| Model | qwen3:14b |
| Prompt version | incident-analysis-v1 |
| Latency | 11144 ms |
| Input tokens | 158 |
| Output tokens | 285 |
| Total tokens | null |
| Smoke-test `AI_TIMEOUT` | 60s |
| Output limit | 450, respected |
| Completion | Answer complete |
| OpenAI API usage for this call | None |

The 60s timeout was a smoke-test override; the application default remains 20s.
The null total is preserved, not synthesized from the reported input/output counts.
No new live call was made to record this evidence. No credentials, full prompt,
incident description or full model response are retained here.

Quality observation: the response was useful and structured in presentation, but
some hypotheses were more general and speculative than in the observed OpenAI
response. This is a single observation, not a benchmark or a general provider
ranking. Comparative evaluation will be a separate phase. Structured presentation
does not establish schema-validated structured output, which was absent at that stage.

Acceptance evidence now covers successful OpenAI and Ollama model calls, usage metadata, fake
replacement, safe provider-error handling, and offline transport-timeout wiring
and 504 translation. Tests inspect actual OkHttp timeout configuration and simulate
transport exceptions without network calls or sleep. Live timeout expiry has not
been measured. At the end of Phase 2, structured-output conversion was not implemented,
so the status remained **Proposed** under the existing acceptance criteria.

See [implementation boundaries](../architecture/ai-integration.md).

## Structured-output implementation — 2026-09-28

Both providers now request the same canonical schema through native request options:
OpenAI strict JSON Schema response format and Ollama `format`. A dedicated strict
Jackson reader and application validator gate every returned analysis; REST now
exposes `analysis` and metadata. Offline tests cover conversion, semantic rejection
and safe errors. No live structured-output request was made and no retry/repair was
added during that implementation step. Historical v1 smoke tests did not establish
v2 behavior, so the status remained Proposed until the evidence below was supplied.

## Live structured-output smoke tests

The project owner reported the following two real smoke tests using
`incident-analysis-v2`. These are supplied observations; no additional live model
calls were made to document them. Test dates were not supplied.

| Observation | OpenAI | Ollama |
|---|---|---|
| HTTP status | 200 | 200 |
| provider | openai | ollama |
| model | gpt-6-luna | qwen3:14b |
| promptVersion | incident-analysis-v2 | incident-analysis-v2 |
| Schema conversion | Passed | Passed |
| Semantic validation | Passed | Passed |
| AI_MAX_OUTPUT_TOKENS | 1000 | 1000 |
| latencyMs | Not supplied | 25458 |
| inputTokens | Not supplied | 310 |
| outputTokens | Not supplied | 662 |
| totalTokens | Not supplied | null |

The Ollama response contained 3 probable causes, 4 consecutively numbered
investigation steps and uncertainties. Its output token limit of 1000 was not
reached. The null total is preserved, not calculated from input/output counts.
No OpenAI latency or token usage is inferred, and no timeout override is inferred
for either structured test. No full prompt, incident title/description, full model
response, API key or other credentials are retained in this evidence.

### Qualitative observation and evaluation follow-up

These are individual smoke tests, not a benchmark or a full evaluation. Ollama
produced a usable structured response, but assigned HIGH to a connection-pool
misconfiguration hypothesis without sufficient evidence. Its recommendation to
increase pool size requires checking PostgreSQL capacity first; otherwise it may
increase contention. These reported observations are input to a future evaluation
phase, not a structured-output runtime defect. Schema conversion and semantic
validation establish contract compliance, not factual correctness or calibrated
likelihood.

## Acceptance outcome

Status is **Accepted**. The following criteria are fulfilled:

- Spring AI integration through the pinned framework and provider adapters;
- a provider-neutral `IncidentAnalysisGateway`, replaceable with a fake in tests;
- both OpenAI and Ollama providers;
- an immutable, versioned prompt contract;
- native structured output using one shared JSON Schema;
- strict conversion followed by full semantic validation;
- transport-level timeout handling and safe provider-error translation, verified offline;
- token and latency metadata, preserving unavailable token counts as null;
- successful live structured-output smoke tests for both providers, reported above.

Acceptance concerns the framework and integration boundaries. It does not establish
production reliability or model-quality equivalence. Retries, repair, fallback,
analysis-result persistence and full evaluation are not implemented and remain
known limitations or future work. Existing incident persistence is separate.
Live timeout expiry has not been measured; the recorded timeout evidence remains
transport configuration and offline exception/504 tests.

## Revisit when

Revisit or supersede this decision if provider compatibility or future evaluation
shows that Spring AI cannot meet the required behavior without excessive workarounds.
