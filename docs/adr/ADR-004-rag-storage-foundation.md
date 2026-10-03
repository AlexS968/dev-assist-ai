# ADR-004: RAG storage foundation

Status: **Accepted**

## Context

Dev Assistant serves multiple applications. Their verified architecture notes, guides and runbooks must not form an undifferentiated corpus: retrieval across systems can produce misleading advice. A KnowledgeBase is a logical boundary for one application or system, identified by a stable key independent of its display name. It may be empty because a newly onboarded system need not have approved documentation. Missing documentation must not block ordinary incident analysis. LLM answers and incident descriptions are not automatically promoted to trusted documentation.

## Decision

Use PostgreSQL 17 with pgvector and three persistence tables: knowledge_bases, knowledge_documents and knowledge_chunks. Metadata and future vectors can be stored in the same transactional database. This reuses existing PostgreSQL operations, Flyway, backup and access controls. Documents have an external ID unique only within their KnowledgeBase. Child-to-parent relations are unidirectional. Deleting a KnowledgeBase cascades to its documents and chunks; deleting a document cascades to its chunks, because children have no independent meaning.

Do not link Incident to KnowledgeBase yet. Selecting a base for an incident changes product behavior and the REST contract, so it needs a separate decision. Do not add a vector column, dimension or ANN index before selecting an embedding model. A model or dimension change may require regenerating embeddings and reindexing documents.

## Alternatives

- A separate vector database offers specialized scaling, but adds an operational system and cross-store consistency work.
- PostgreSQL with pgvector keeps metadata and future vectors together and is sufficient for the current scale.
- A managed vector store reduces infrastructure maintenance, but adds vendor dependency, cost and data movement.

## Consequences and limits

One PostgreSQL instance is simpler to operate, but vector workloads may eventually compete with transactional workloads. At larger scale, index size, query latency and isolation must be measured and storage reconsidered. This foundation contains no ingestion, embeddings, similarity search, retrieval, grounding or citations. It is not yet a working RAG pipeline. Runtime fallback when no relevant context exists will be implemented with retrieval; current AI analysis remains unchanged.
