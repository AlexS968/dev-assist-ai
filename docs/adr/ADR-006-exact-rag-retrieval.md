# ADR-006: Exact profile-isolated cosine retrieval

Status: Accepted

## Context

Knowledge chunks may contain vectors from different providers and models in the same 768-dimensional column. Equal dimensions do not make those spaces comparable. The initial corpus is small, and relevance threshold and topK need evaluation before optimizing search.

## Decision

Use an explicit provider-neutral retrieval application service. Check KnowledgeBase existence and whether it contains a vector for the active provider, model and dimensions before embedding the query. If none exists, return an empty result without provider cost. Otherwise embed once with QUERY purpose outside a database transaction and perform a short read-only exact PostgreSQL search. Hibernate Vector's `cosine_distance` HQL function maps to pgvector `<=>`; similarity is `1 - distance`. Filter by KnowledgeBase, exact case-sensitive profile and similarity threshold, order by distance ascending then chunk ID, and apply topK in PostgreSQL. Defaults are topK 5, maximum 20 and threshold 0.70. The threshold is an engineering starting point requiring calibration on an evaluation corpus.

Do not create HNSW or IVFFlat indexes yet. Exact search provides a deterministic baseline and avoids a shared ANN index that could mix incompatible vector spaces. Retrieved content remains untrusted and is not passed to Incident analysis or an LLM. No REST endpoint, scheduler, retry or provider fallback is introduced.

## Consequences

Exact search scans compatible vectors in the selected base and may become slow as the corpus grows. Consider ANN only after measurable growth and a confirmed latency problem, supported by `EXPLAIN ANALYZE` and a defined profile-specific index strategy. Re-evaluate topK and threshold against a representative corpus before changing them.
