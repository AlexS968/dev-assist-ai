# Dev Assistant AI

Dev Assistant AI is a production-oriented Java backend for investigating software
incidents.

A developer submits an incident description, and the system will progressively
build an evidence-based investigation using structured LLM output, technical
runbooks, retrieval-augmented generation, and controlled operational tools.

The project is also a hands-on implementation of an AI-enabled backend system.
It focuses on architecture, reliability, testing, observability, security, and
deployment rather than wrapping an LLM API in a chat interface.

## Project status

**Phase 3 — Native structured incident analysis**

Currently implemented:

- Java 21 and Spring Boot 4.1.1;
- incident creation, retrieval, listing and status API;
- Spring AI 2.0.1 OpenAI/Ollama adapters with native JSON schema and offline tests;
- PostgreSQL;
- Flyway migrations;
- Docker Compose development database;
- Testcontainers integration tests;
- Spring Boot Actuator health endpoints;
- multi-stage Docker image;
- non-root runtime container;
- GitHub Actions CI.

The incident REST API includes synchronous analysis of an existing incident through
a provider-neutral gateway. Analysis results are returned without persistence.

## Technology

- Java 21
- Spring Boot 4.1
- Maven
- PostgreSQL 17
- Flyway
- Docker and Docker Compose
- Testcontainers
- GitHub Actions

Spring AI 2.0.1 is managed through its official BOM. ADR-003 is **Accepted** after
reported live structured-output smoke tests succeeded with gpt-6-luna and local
qwen3:14b using incident-analysis-v2. Transport timeout handling is tested offline; see
[ADR-003](docs/adr/ADR-003-ai-framework.md) for the recorded evidence.

## Architecture

Dev Assistant AI starts as a modular monolith: one Spring Boot application, one
deployment unit, and explicit boundaries between business capabilities.

See:

- [Current architecture](docs/architecture/README.md)
- [Architecture Decision Records](docs/adr/README.md)

## Prerequisites

- Java 21
- Docker Desktop or another compatible Docker Engine
- Git

A separate Maven installation is not required. The project includes Maven
Wrapper.

## Running locally

Start PostgreSQL:

```bash
docker compose up -d
```

Check its status:

```bash
docker compose ps
```

For the default OpenAI provider, provide `OPENAI_API_KEY` in your shell or IDE
environment. Alternatively, select `AI_PROVIDER=ollama` without a key (see Configuration).
Then run the application:

```bash
./mvnw spring-boot:run
```

Check application health:

```bash
curl http://localhost:8080/actuator/health
```

Expected result:

```json
{
  "groups": ["liveness", "readiness"],
  "status": "UP"
}
```

Stop the development database:

```bash
docker compose down
```

The named PostgreSQL volume is preserved by `docker compose down`. Do not add
the `--volumes` option unless the local database should intentionally be deleted.

## API documentation

Start PostgreSQL, then run the application:

```bash
docker compose up -d
./mvnw spring-boot:run
```

- [Swagger UI](http://localhost:8080/swagger-ui.html)
- [OpenAPI JSON](http://localhost:8080/v3/api-docs)

For manual checks, open [requests/incidents.http](requests/incidents.http) in
IntelliJ IDEA with HTTP Client support. Run the requests from top to bottom using
the gutter Run icons, or use Run All Requests. The creation request stores the
incident ID in `client.global`; subsequent requests reuse it. Re-run the scenario
from creation to obtain a fresh incident. The final two requests intentionally
return 409 and 400.

## Incident analysis

`POST /api/v1/incidents/{id}/analysis` takes an incident UUID and no request body.
Only the stored title and description are sent to the AI gateway. A successful
200 response has this shape (illustrative values):

```json
{
  "analysis": {
    "summary": "Database requests are timing out.",
    "probableCauses": [{
      "title": "Connection pool exhaustion",
      "explanation": "Long-running queries may be holding connections.",
      "likelihood": "MEDIUM",
      "evidenceToCheck": ["Inspect active connection metrics."]
    }],
    "investigationSteps": [{
      "order": 1,
      "action": "Inspect pool metrics during the incident.",
      "rationale": "Saturation would support the hypothesis."
    }],
    "uncertainties": ["Database logs are unavailable."]
  },
  "provider": "openai",
  "model": "gpt-6-luna",
  "promptVersion": "incident-analysis-v2",
  "generatedAt": "2026-09-27T12:00:00Z",
  "latencyMs": 1250,
  "attemptCount": 1,
  "inputTokens": 120,
  "outputTokens": 80,
  "totalTokens": 200
}
```

Unavailable token counts are `null`. `generatedAt` is application receipt time;
latency uses a monotonic timer; after repair it covers both attempts. The result is not saved and each POST generates
a new analysis. Provider calls run outside DB transactions.

Unknown incidents return the existing 404 `application/problem+json` response.
Provider failures, empty output, JSON conversion errors or semantic validation failures return 502 with the fixed detail
`Incident analysis is temporarily unavailable.` Transport timeouts return 504 with
`Incident analysis timed out. Please try again later.` Provider internals are not exposed.
With real runtime credentials, this endpoint invokes the provider; automated tests
use mocks and do not consume tokens.

## Running tests

Docker must be running because integration tests use Testcontainers.

The tests do not use the PostgreSQL instance from `compose.yaml`. They start an
isolated temporary PostgreSQL container and apply the same Flyway migrations
used by the application.

Run all checks:

```bash
./mvnw clean verify
```

## Building the application image

Build the production image:

```bash
docker build -t dev-assist-ai:local .
```

The image uses a multi-stage build. Maven and source code are present only during
the build stage. The final runtime image contains a Java runtime and the
executable application JAR.

The application runs as a non-root `app` user.

## Configuration

Local defaults are provided for development:

| Environment variable | Default |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/dev_assist` |
| `DB_USERNAME` | `dev_assist` |
| `DB_PASSWORD` | `dev_assist` |
| `AI_PROVIDER` | `openai` (allowed: `openai`, `ollama`) |
| `OPENAI_API_KEY` | Required only for `openai`; no default |
| `OPENAI_MODEL` | `gpt-6-luna` |
| `OLLAMA_MODEL` | `qwen3:14b` |
| `OLLAMA_BASE_URL` | `http://localhost:11434` |
| `AI_PROMPT_VERSION` | `incident-analysis-v2` |
| `AI_MAX_OUTPUT_TOKENS` | `1000` |
| `AI_TIMEOUT` | `20s` |

The database defaults are intended only for the local Docker Compose database. Deployed
environments must provide their own credentials through environment variables or
a secrets manager.

Application-owned `app.ai.openai-model` binds `OPENAI_MODEL` and supplies
`spring.ai.openai.chat.model`. The key is read directly from `OPENAI_API_KEY`;
it is never stored in application properties or logged by application code.
`.env.example` contains a fictional key. Spring Boot does not load `.env`
automatically: export the variables or configure them in your IDE.

Only the selected chat provider is wired. SDK retries remain disabled. Startup
constructs clients but does not contact either provider or pull Ollama models. All tests use a fictional key
and a loopback base URL where required; Ollama contexts need no key at all.
Endpoint tests replace the gateway and adapter tests mock the model/API. No OpenAI account
or tokens are required for `./mvnw clean verify`.

Prompts are loaded at startup from `prompts/incident-analysis/v2/system.st` and
`user.st`. `app.ai.prompt-version` identifies the immutable template pair and is
returned as `promptVersion`. Only `incident-analysis-v2` is compatible with the structured runtime;
blank, unknown and v1 versions fail startup. The v1 resources are retained for history. New behavior requires a new version.

`app.ai.max-output-tokens` accepts 1–16384 tokens (an application guardrail, not a
claim about every model's capacity). The adapters set OpenAI `maxCompletionTokens`
or Ollama `num_predict` for every request. Ollama sends the official `think=false`
option, supported by qwen3, without modifying the prompt. No temperature is set. The default budget is now 1000 tokens to reduce JSON truncation.
This permits higher cost and latency; it is a ceiling, not a target response length.
`AI_MAX_OUTPUT_TOKENS` remains configurable. For reasoning models, the limit
also budgets reasoning tokens: visible output can be shorter or truncated.

`app.ai.timeout` is a Duration (for example `20s`, `750ms` or `PT20S`). It is
required and validated from 1 ms to 2147483647 ms, the supported OkHttp range;
blank, zero and negative values fail startup. It feeds `spring.ai.openai.timeout`
and the SDK/OkHttp whole-call deadline for OpenAI. For Ollama it configures
synchronous HTTP connect/read timeouts; these bound connection establishment and
each blocking read, not the total wall-clock call. No background future timeout is used and
SDK retries remain zero. Cancelling the local call cannot guarantee that the
provider stops processing or billing work already received.

Switch manually in the shell or IDE environment before restarting the application:

```bash
# Local Ollama: no OPENAI_API_KEY is needed.
unset OPENAI_API_KEY
export AI_PROVIDER=ollama
export OLLAMA_MODEL=qwen3:14b
export OLLAMA_BASE_URL=http://localhost:11434
```

To switch back, set `AI_PROVIDER=openai` and provide `OPENAI_API_KEY` securely in
your environment. `OPENAI_MODEL` defaults to `gpt-6-luna`. Selection is explicit;
there is exactly one gateway and no automatic fallback. Unknown providers fail startup.
`AI_MAX_OUTPUT_TOKENS` sets the shared output limit for both providers.
All providers share `AI_PROMPT_VERSION` and `AI_TIMEOUT`.

Run Ollama natively on macOS, outside Docker, to use Apple Silicon acceleration.
The model must already be installed; the application does not pull it. If this
application runs in Docker Desktop, set `OLLAMA_BASE_URL=http://host.docker.internal:11434`
and configure host access appropriately. A 14B model may need a longer timeout on
cold start. The first live application Ollama smoke test on 2026-09-27 returned
HTTP 200 in 11144 ms with 285 output tokens within the 450-token limit and a
complete response, using `AI_TIMEOUT=60s`. OpenAI was not used for that call.
This is integration evidence, not a comparative quality benchmark.
Ollama reports input/output counts separately; absent counts, including an
unreported total, are returned as null.

See [AI infrastructure and boundaries](docs/architecture/ai-integration.md).

## Structured-output smoke-test results

The project owner reported HTTP 200 for both `openai` / `gpt-6-luna` and
`ollama` / `qwen3:14b`, using `incident-analysis-v2` and an output budget of 1000.
Both structured JSON responses passed schema conversion and semantic validation.
OpenAI latency and token usage were not supplied and are not inferred.

Ollama reported `latencyMs=25458`, `inputTokens=310`, `outputTokens=662` and
`totalTokens=null`. Its response contained 3 probable causes, 4 consecutively
numbered investigation steps and uncertainties; the 1000-token limit was not reached.

These individual smoke tests are not a benchmark or full evaluation. Ollama's
structured response was usable, but assigned HIGH to a connection-pool
misconfiguration hypothesis without sufficient evidence. Increasing pool size
requires checking PostgreSQL capacity first to avoid worsening contention.
These observations inform future evaluation, rather than indicating a
structured-output runtime defect. These smoke tests predate the controlled repair
policy and do not establish live repair behavior.

See [detailed evidence](docs/architecture/ai-integration.md#live-structured-output-smoke-tests)
and [ADR-003 acceptance](docs/adr/ADR-003-ai-framework.md#acceptance-outcome).
No additional live calls were made for this documentation update; no full prompts,
incident title/description, model responses or credentials are recorded.

### Happy-path checks after repair orchestration

The project owner reported these additional live results with
`promptVersion=incident-analysis-v2`:

| Field | OpenAI | Ollama |
|---|---|---|
| HTTP status | 200 | 200 |
| provider / model | openai / gpt-6-luna | ollama / qwen3:14b |
| latencyMs | 8619 | 23141 |
| inputTokens / outputTokens | 424 / 667 | 310 / 596 |
| totalTokens | 1091 | null |
| attemptCount | 1 | 1 |

Both passed structured conversion and semantic validation, stayed below the
`AI_MAX_OUTPUT_TOKENS=1000` output limit, and did not invoke repair. Both providers
therefore worked on the happy path after the orchestration change; these runs do
not prove the live repair path. Repair scenarios are covered by deterministic
automated tests. Invalid live output was not artificially provoked because of
nondeterminism and additional cost.

OpenAI phrased hypotheses more cautiously in these runs. Ollama again assigned HIGH
without sufficient evidence, this time to a connection-leak hypothesis. This is an
observation for future evaluation, not a structured-output error. One run per
provider is not a benchmark. ADR-003 remains **Accepted**. See
[the detailed record](docs/architecture/ai-integration.md#live-smoke-tests-after-the-repair-policy).
No new live calls or sensitive source data were added for this documentation update.

## Roadmap

1. Engineering foundation
2. Incident REST API and persistence
3. LLM integration
4. Validated structured output and reliable orchestration
5. Runbook ingestion and RAG
6. Controlled operational tool calling
7. Evaluation and AI observability
8. Security and production hardening
9. Cloud deployment
10. MCP integration, if justified by an external client use case
11. Controlled agentic investigation workflow, if justified by evaluation

AI capabilities are added only when they solve a concrete product problem.

## Controlled repair

A provider-neutral policy between service and gateway allows **at most two total
attempts**, always enabled. Only conversion or semantic validation failure triggers
one repair. Success, empty response, timeout, provider/transport errors and unknown
incidents do not. A second failure is final; existing safe 502/504 mappings remain.
There are no loops, background calls, sleep, SDK retries or provider fallback.

The second call uses the original incident data and same canonical native schema,
with a fixed shared repair instruction. No invalid model response or error diagnostics
are sent back to the provider. The prompt version remains `incident-analysis-v2`.

`attemptCount` is 1 or 2. First-success metrics are unchanged; repaired-success
latency covers the whole orchestration. Each token field sums both attempts only if
both values are available, otherwise it is null. Results and metrics are not persisted.
Repair may roughly double cost/latency; timeout and token budgets remain per attempt.
It improves contract reliability, not factual accuracy. See
[repair details](docs/architecture/ai-integration.md#controlled-structured-output-repair).

## Known limitations

The current AI step provides synchronous, schema-constrained structured analysis.
OpenAI uses native strict JSON Schema; Ollama uses the same schema via `format`.
A dedicated Jackson reader rejects malformed/trailing JSON, unknown properties,
missing/null required fields and coercion; application validation then checks the
complete graph and step ordering. One controlled repair is allowed only for initial
conversion/validation failures; SDK retries and provider fallback remain disabled.
The old `content` response field is removed. `likelihood` is qualitative model
prioritization, not probability or measured confidence. No `rootCause` is asserted.
See the [structured contract](docs/architecture/structured-incident-analysis.md).

- analysis is synchronous and results are not persisted;
- successful v2 smoke tests, including the post-repair-policy happy path, establish integration evidence, not a benchmark or full evaluation;
- schema validity cannot establish factual correctness; 1000 tokens can still truncate output;
- timeout expiry itself is tested without network I/O;
- no authentication or authorization;
- no vector search;
- no operational tools;
- no cloud deployment;
- Docker Compose currently starts PostgreSQL only;
- local database credentials are development defaults and are not suitable for
  deployment.

## Development workflow

Changes are developed in short-lived branches and merged into `main` through
Pull Requests after CI passes.

Examples:

```text
feature/incident-api
chore/database-migration
docs/architecture-update
fix/flyway-configuration
```

`main` should remain in a buildable state.

## License

This project is licensed under the MIT License.