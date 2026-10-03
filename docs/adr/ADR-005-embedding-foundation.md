# ADR-005: Separate embedding providers with a 768-dimensional storage profile

Status: **Accepted**

## Context

Incident analysis and knowledge embeddings have different workloads and may use different providers. The chat choice (`AI_PROVIDER`) must not determine the embedding choice (`RAG_EMBEDDING_PROVIDER`). OpenAI `text-embedding-3-small` supports a requested 768-dimensional output; Ollama `nomic-embed-text` uses 768 dimensions. Both can occupy PostgreSQL `vector(768)`, but equal dimensions do not make their vector spaces comparable.

## Decision

Configure chat and embedding providers separately. The embedding gateway accepts ordered batches and returns validated ordered vectors without exposing Spring AI types. Its active deployment profile consists of provider, model and dimensions. A stored embedding is compatible only if all three match. Changing provider or model makes old embeddings stale; they are retained, and a future re-embedding process must replace them before retrieval can use them. No embedding profile is stored on KnowledgeBase yet.

Embedding runtime is opt-in through `RAG_ENABLED=true`; the default is false.
When disabled, conditional configuration creates no gateway, embedding profile,
or RAG-specific provider client and does not validate unused embedding settings
or require embedding credentials. PostgreSQL storage and controlled ingestion
remain available. The built-in Spring AI embedding auto-configuration is disabled
with `spring.ai.model.embedding=none`; chat configuration remains independent.
No no-op gateway is installed. Ordinary incident analysis works without a
KnowledgeBase or embedding provider.

The migration adds nullable `vector(768)` and provider, model, dimensions and timestamp fields to each chunk. All five fields are either present or absent. Existing and newly ingested chunks remain unembedded until an explicit later operation supplies vectors. Ingestion does not call an embedding provider automatically: this avoids unexpected network calls, cost and latency, and keeps document persistence independent of provider availability.

No ANN index is created. The corpus is initially small, models cannot be mixed in one similarity space, and profile-filtered exact search can be evaluated before choosing HNSW or IVFFlat.

## Consequences

OpenAI offers managed operations and may add per-token cost, latency and external transfer of chunk text. Ollama can keep text local but requires model installation and local compute. Both require transport-level timeout and safe error handling. This foundation has no background embedding or reindexing job, no retrieval and no knowledge context in the analysis prompt. A model or dimension change requires schema review and re-embedding.
