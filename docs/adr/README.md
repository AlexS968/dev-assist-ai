# Architecture Decision Records

Architecture Decision Records document significant architectural decisions made
during the development of Dev Assistant AI.

An ADR is not intended to describe every implementation detail. It records the
problem, considered alternatives, chosen direction, consequences, and remaining
risks.

## Statuses

- `Proposed` — under consideration or awaiting implementation evidence
- `Accepted` — approved and currently applicable
- `Superseded` — replaced by a later ADR
- `Rejected` — considered but not selected

## Decisions

| ADR | Title | Status |
|---|---|---|
| [ADR-001](ADR-001-modular-monolith.md) | Use a modular monolith | Accepted |
| [ADR-002](ADR-002-postgresql.md) | Use PostgreSQL as the primary datastore | Accepted |
| [ADR-003](ADR-003-ai-framework.md) | Select the AI application framework | Proposed |
| [ADR-004](ADR-004-rag-storage-foundation.md) | RAG storage foundation | Accepted |
| [ADR-005](ADR-005-embedding-foundation.md) | Separate embedding providers and 768-dimensional profile | Accepted |
| [ADR-006](ADR-006-exact-rag-retrieval.md) | Exact profile-isolated cosine retrieval | Accepted |
| [ADR-007](ADR-007-incident-knowledge-base-association.md) | Explicit optional Incident–KnowledgeBase association | Accepted |
| [ADR-008](ADR-008-grounded-incident-analysis.md) | Grounded Incident analysis and backend-validated citations | Accepted |
