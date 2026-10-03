# RAG storage foundation

A KnowledgeBase holds controlled documentation for one application or system. Its stable `key` identifies the system; `name` is display text. Documents are scoped to a base, and chunks belong to documents. The same `external_id` can appear in different bases, but not twice within one base. A base may have no documents. Database foreign keys cascade deletion from base to document to chunk.

Implemented: logically isolated KnowledgeBase persistence, PostgreSQL 17 with pgvector extension, document and chunk persistence, JSONB metadata, database uniqueness and nonnegative index constraints, and empty bases. Hibernate maps metadata as `Map<String, String>` through its JSON type; the entity copies maps on input and output.

Not implemented: KnowledgeBase management REST API; Incident association; ingestion; content normalization; chunking; embeddings; vector column; vector index; similarity search; retrieval; analysis behavior with or without relevant context; prompt grounding; citations; prompt injection protection; RAG evaluation. The current incident analysis endpoint continues without RAG.

See [ADR-004](../adr/ADR-004-rag-storage-foundation.md).
