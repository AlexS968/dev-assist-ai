# ADR-001: Use a modular monolith

- Status: Accepted
- Date: 2026-09-20

## Context

Dev Assistant AI will contain several capabilities:

- incident management;
- AI-assisted analysis;
- technical knowledge retrieval;
- operational tool execution;
- evaluation and observability.

These capabilities need clear boundaries, but the project is developed by one
person and is expected to remain relatively small. Independent deployment,
scaling, and ownership of individual capabilities are not currently required.

## Decision

Build the system as one Spring Boot application and one deployable artifact.

Organize application code by business capability rather than by global technical
layers. The expected top-level packages are:

- `incident`;
- `analysis`;
- `knowledge`;
- `operations`;
- `evaluation`;
- `observability`;
- `shared`.

Capabilities may contain their own API, application, domain, and infrastructure
code when those distinctions become useful.

Interactions between capabilities should happen through explicit Java APIs rather
than direct access to internal implementation classes.

## Alternatives considered

### Multiple microservices

Rejected because the project does not currently require independent deployment,
scaling, data ownership, or team ownership. Microservices would add network
communication, distributed failure modes, deployment overhead, and more complex
testing without solving an existing problem.

### Traditional global layers

A structure based only on global `controller`, `service`, and `repository`
packages was considered. It is simple initially, but business capabilities
become mixed together as the application grows.

## Consequences

### Positive

- simple local development and deployment;
- transactions remain local;
- integration tests are straightforward;
- business capabilities can still have explicit boundaries;
- the application can be split later if a concrete scaling or ownership need
  appears.

### Negative

- module boundaries are not enforced by network isolation;
- careless dependencies can create coupling between capabilities;
- the whole application is deployed and scaled as one unit.

## Risks and mitigations

Architectural boundaries may gradually erode.

Mitigations:

- package code by capability;
- keep capability internals package-private where practical;
- document important dependencies;
- add architecture tests if boundary violations become a recurring problem.

## Revisit when

Reconsider this decision if a capability requires independent scaling,
deployment, availability, data ownership, or separate team ownership.