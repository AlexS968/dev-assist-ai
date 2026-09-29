# ADR-003: Select the AI application framework

- Status: Proposed
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

## Proposed decision

Use Spring AI as the primary AI application framework, subject to validation in
a small integration spike during the first LLM phase.

Keep application-specific boundaries such as `IssueAnalysisGenerator` around
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

## Validation required before acceptance

The integration spike must demonstrate:

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
added. Historical v1 smoke tests do not establish v2 behavior. Status remains
**Proposed** pending real structured smoke tests for OpenAI and Ollama.

## Revisit when

Change the status to `Accepted` after the integration spike satisfies the remaining
validation criteria. Reject or supersede this ADR if Spring AI cannot meet the
required behavior without excessive workarounds.
