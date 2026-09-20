# ADR-002: Use PostgreSQL as the primary datastore

- Status: Accepted
- Date: 2026-09-20

## Context

The application needs durable storage for incidents and, in later phases:

- structured AI analyses;
- analysis execution status;
- prompt and model metadata;
- retrieved document references;
- tool execution records;
- evaluation results.

The project also plans to implement retrieval-augmented generation. A vector
store will therefore be required in a later phase.

Development and test environments should behave similarly to the production
database.

## Decision

Use PostgreSQL as the primary relational datastore.

Use:

- Flyway for versioned schema migrations;
- Spring Data JPA for ordinary persistence;
- Testcontainers for integration tests;
- Docker Compose for local development.

Hibernate validates the schema but does not create or update it automatically:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate