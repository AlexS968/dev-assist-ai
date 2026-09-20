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

## Revisit when

Change the status to `Accepted` after the integration spike satisfies the
validation criteria. Reject or supersede this ADR if Spring AI cannot meet the
required behavior without excessive workarounds.
