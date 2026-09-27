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

**Phase 2 — Incident analysis service and REST endpoint**

Currently implemented:

- Java 21 and Spring Boot 4.1.1;
- incident creation, retrieval, listing and status API;
- Spring AI 2.0.1 OpenAI adapter and offline tests;
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

Spring AI 2.0.1 is managed through its official BOM. ADR-003 remains Proposed
until the later integration spike validates real provider behavior.

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

Provide `OPENAI_API_KEY` in your shell or IDE environment, then run the application:

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
  "content": "Check database connectivity.",
  "provider": "openai",
  "model": "gpt-6-luna",
  "generatedAt": "2026-09-27T12:00:00Z",
  "latencyMs": 1250,
  "inputTokens": 120,
  "outputTokens": 80,
  "totalTokens": 200
}
```

Unavailable token counts are `null`. `generatedAt` is application receipt time;
latency uses a monotonic timer. The result is not saved and each POST generates
a new analysis. Provider calls run outside DB transactions.

Unknown incidents return the existing 404 `application/problem+json` response.
Provider failures or empty content return 502 with the fixed detail
`Incident analysis is temporarily unavailable.` Provider internals are not exposed.
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
| `OPENAI_API_KEY` | Required; no production default |
| `OPENAI_MODEL` | `gpt-6-luna` |

The defaults are intended only for the local Docker Compose database. Deployed
environments must provide their own credentials through environment variables or
a secrets manager.

Application-owned `app.ai.model` binds `OPENAI_MODEL` and supplies
`spring.ai.openai.chat.model`. The key is read directly from `OPENAI_API_KEY`;
it is never stored in application properties or logged by application code.
`.env.example` contains a fictional key. Spring Boot does not load `.env`
automatically: export the variables or configure them in your IDE.

Only the chat model is enabled. SDK retries are disabled for this step. Startup
constructs the client but does not contact OpenAI. All tests use a fictional key
and a loopback base URL; endpoint tests replace the gateway and adapter tests mock
the model. No OpenAI account
or tokens are required for `./mvnw clean verify`.

See [AI infrastructure and boundaries](docs/architecture/ai-integration.md).

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

## Known limitations

The current AI step provides synchronous, unstructured analysis.

- analysis is synchronous and results are not persisted;
- real model availability and response behavior have not been validated;
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