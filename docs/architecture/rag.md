# RAG storage and controlled Markdown ingestion

A KnowledgeBase holds controlled documentation for one application or system. Its stable `key` identifies the system; `name` is display text. Documents are scoped to a base, and chunks belong to documents. The same `external_id` can appear in different bases, but not twice within one base. An empty KnowledgeBase is valid. Database foreign keys cascade deletion from base to document to chunk.

The provider-neutral `KnowledgeDocumentIngestionService` accepts a command containing a KnowledgeBase UUID, external ID, title, source type, optional source URI and Markdown content. It reads neither local files nor URLs. There is no public upload API yet because authentication, authorization, upload limits and an external ingestion contract have not been defined. LLM responses and incident descriptions are never automatically treated as documentation.

## Normalization and hashing

Before persistence, input is limited to 1,000,000 UTF-16 code units. Normalization applies Unicode NFC, converts CRLF and CR to LF, removes trailing spaces and tabs outside fenced code blocks, removes leading and trailing empty lines, and limits internal runs to two empty lines. Markdown headings, lists, punctuation, case and fenced code contents are preserved. HTML is not sanitized. The normalized result must be nonblank. SHA-256 of its UTF-8 bytes is stored as a lowercase 64-character document hash. Each chunk's hash uses its exact stored content. Repeating the same input produces the same hashes.

## Chunking

Defaults are `app.rag.chunking.max-chars=1500` and `overlap-chars=200`, overridable by `RAG_CHUNK_MAX_CHARS` and `RAG_CHUNK_OVERLAP_CHARS`. The maximum must be 200–10000; overlap must be nonnegative and smaller than maximum. The deterministic splitter searches the latter half of each window for a boundary, in priority order: Markdown heading start, paragraph break, line break, then whitespace. If none exists it hard-splits at the maximum without splitting a UTF-16 surrogate pair. A heading boundary starts the next chunk with that heading when it fits. No tokenizer or model-specific rule is used.

Chunks are exact substrings of normalized document content. Metadata `characterStart` is inclusive and `characterEnd` exclusive, measured in UTF-16 code units in that normalized content; overlap is represented by intersecting ranges. `chunkIndex` starts at zero, `sourceType` records the document type, and `sectionHeading` records the latest heading at chunk start when present; fenced code lines are not treated as headings. On `UPDATED_METADATA`, a changed document `sourceType` is synchronized into every existing chunk without changing chunk IDs, content, hashes, indices or other metadata. Title-only and URI-only changes leave chunks untouched. After each chunk, the next start is normally `end - overlap`; it advances at least one code unit and never starts within a surrogate pair. This guarantees progress and prevents an overlap-only final chunk.

## Persistence and idempotency

- `CREATED`: save a new document and all chunks in one transaction.
- `UNCHANGED`: content hash and document metadata match; return existing ID and chunk count without touching timestamps or chunks.
- `UPDATED_METADATA`: title, source type or source URI changed but content hash matches; update the document without replacing chunks.
- `UPDATED`: normalized content changed; update document fields and hash, delete prior chunks, flush deletion, then insert and flush replacements in the same transaction. Failure rolls the transaction back.

The database unique constraint on `(knowledge_base_id, external_id)` is the final uniqueness guard. Concurrent first ingestion for one external ID can race after both transactions observe absence; one transaction fails and rolls back safely. Concurrent updates to an existing document are not serialized by an application lock and can overwrite one another or fail on chunk-index uniqueness; callers must serialize such writes until an explicit concurrency policy is added. There is no retry or distributed lock.

Ingestion creates chunks without embeddings. Still pending: public document ingestion, automatic embedding, vector index and live quality calibration. An offline evaluation corpus and explicitly launched live harness are available. Grounded analysis uses bounded untrusted context and backend-validated citations; prompt injection remains a known risk.

## Explicit Incident association and KnowledgeBase API

A KnowledgeBase normally owns documentation for one project or service. `POST /api/v1/knowledge-bases` creates an empty base; `GET /api/v1/knowledge-bases/{id}` and paginated `GET /api/v1/knowledge-bases` expose only base metadata. Keys are trimmed but must already be lowercase machine-readable strings; names are trimmed. Duplicate keys return a safe 409 response. There is no public update, delete, ingestion, embedding or retrieval endpoint. Public deletion needs an authorization and audit policy.

Incident creation accepts a nullable `knowledgeBaseId`, and `PATCH /api/v1/incidents/{id}/knowledge-base` explicitly assigns an existing base or clears the association. The application does not infer a base from title or description. A no-op PATCH preserves `updatedAt`; a real association change updates it. Flyway V5 adds a nullable indexed FK with `ON DELETE SET NULL`: deleting a base through persistence deletes its documents and chunks but preserves linked Incidents with a null FK. One base can serve many Incidents, while an Incident has at most one base. This relation exists even when `RAG_ENABLED=false`. The association alone does not invoke retrieval; analysis uses retrieval only when RAG is enabled. See [ADR-007](../adr/ADR-007-incident-knowledge-base-association.md).

See [ADR-004](../adr/ADR-004-rag-storage-foundation.md).

## Embedding foundation

`RAG_ENABLED` defaults to `false`. The entire embedding configuration is conditional
on `app.rag.enabled=true`; disabled mode has no `KnowledgeEmbeddingGateway`,
`EmbeddingProfile`, OpenAI/Ollama embedding options, or RAG-specific embedding
client. It does not bind or validate unused embedding provider settings or require
embedding credentials. There is no no-op gateway. Storage, Flyway and ingestion
remain active; incident analysis proceeds without a KnowledgeBase or embeddings.
Spring AI's built-in embedding auto-configuration is independently disabled by
`spring.ai.model.embedding=none`, while chat auto-configuration is retained.

| Chat (`AI_PROVIDER`) | `RAG_ENABLED` | Embedding provider | OpenAI key needed |
|---|---|---|---|
| `openai` | `false` | none | for chat |
| `ollama` | `false` | none | no |
| `openai` | `true` | `ollama` | for chat |
| `ollama` | `true` | `openai` | for embeddings |
| `ollama` | `true` | `ollama` | no |

The embedding provider is independent of the incident chat provider. `RAG_EMBEDDING_PROVIDER` defaults to `openai` and accepts `openai` or `ollama`. `OPENAI_EMBEDDING_MODEL` defaults to `text-embedding-3-small`; `OLLAMA_EMBEDDING_MODEL` defaults to `nomic-embed-text`. `RAG_EMBEDDING_DIMENSIONS` defaults to 768 and currently accepts only 768. `RAG_EMBEDDING_TIMEOUT` defaults to `20s`. `RAG_EMBEDDING_BATCH_SIZE` defaults to 32 (1–256); `RAG_EMBEDDING_MAX_CHUNKS_PER_RUN` defaults to 500 (1–10000). These settings are validated only when RAG is enabled. OpenAI embeddings use `OPENAI_API_KEY`; Ollama embeddings reuse `OLLAMA_BASE_URL`. Pull the local model ahead of use with `ollama pull nomic-embed-text`. Provider selection does not change `AI_PROVIDER`.

OpenAI explicitly sends `dimensions=768` in its embedding request and disables SDK retries. Ollama uses Spring AI's `/api/embed` client with an ordered list of inputs in one batch request and a transport-level connect/read timeout. Spring AI's Ollama implementation converts a missing `prompt_eval_count` to zero; the adapter reports zero as unknown (`null`) rather than claiming measured usage. The adapter validates response count, indices, finite values and dimensions, and returns only safe errors. No live request occurs at startup and ingestion does not call the gateway.

Flyway V3 adds nullable `vector(768)` plus provider, model, dimension and timestamp columns. Database checks require the five fields to be all null or all non-null, with dimension 768. Hibernate's official `hibernate-vector` module maps a defensively copied `float[]` using `SqlTypes.VECTOR`. Metadata-only document updates preserve embeddings; content changes replace chunks without embeddings.

A stored embedding is compatible with the active `EmbeddingProfile` only when provider, model and dimensions all match. Equal dimensionality alone is insufficient. The explicit `KnowledgeEmbeddingOrchestrator.embedKnowledgeBase(id)` application service selects at most the configured run limit in deterministic document/index/ID order, reading one extra candidate to report `limitReached`. It sends ordered DOCUMENT batches outside a database transaction. A separate writer reloads each chunk, checks base, content hash and compatibility, then saves in its own short transaction. Flyway V4 and Hibernate `@Version` prevent lost updates; a conflict skips only that chunk. Per-chunk transactions cost additional round trips but isolate conflicts. Repeated runs skip compatible chunks and gradually replace incompatible profiles. Later provider failure leaves earlier batches committed, so the run can be restarted without clearing old vectors. No automatic retry, scheduler, background job or prompt grounding exists yet. Retrieval filters by the active profile. Each missing or stale chunk requires a paid or local embedding generation. An empty base returns a successful zero-count result with unknown token usage and never calls the provider. See [ADR-005](../adr/ADR-005-embedding-foundation.md).

Embedding requests carry `DOCUMENT` or `QUERY` purpose. OpenAI `text-embedding-3-small` receives unchanged text. Nomic's [model card](https://huggingface.co/nomic-ai/nomic-embed-text-v1.5) requires `search_document: ` and `search_query: ` task prefixes. The [Ollama model page](https://ollama.com/library/nomic-embed-text) shows plain input examples and lists no task-prefixing template; the current `/api/embed` adapter therefore adds the prefix explicitly. Prefixes are applied only to provider input, leaving stored chunk content and content hash unchanged. Verify this assumption if replacing the Ollama model or template.

## Exact vector retrieval

`KnowledgeRetrievalService.retrieve(request)` is an explicit application call, with no REST endpoint or scheduled job. It validates a KnowledgeBase ID and query (maximum 10000 UTF-16 code units), checks base existence, then checks whether the base contains any embeddings matching the active provider, model and dimensions. An empty or wholly stale corpus returns an immutable empty result without calling the provider. Otherwise the service embeds the query once with `QUERY` purpose, validates the nonzero finite vector and active profile, and invokes a short read-only database search. The provider call is outside any database transaction. Provider failures leave database state unchanged; there are no retries or provider fallback.

Hibernate Vector 7.4.5 registers `cosine_distance` for PostgreSQL as pgvector `<=>`. The HQL search binds the query `float[]` as `StandardBasicTypes.VECTOR` and evaluates `1.0 - cosine_distance` as similarity in PostgreSQL. It filters by KnowledgeBase ID and exact case-sensitive provider, model and dimensions, then by similarity threshold, and orders by distance ascending and chunk ID ascending before applying topK. NULL and stale embeddings are excluded. Returned document and chunk metadata are immutable; content is untrusted source material and is not interpreted, fetched as a URL, or treated as instructions. Eligible Incident analysis passes selected content as bounded untrusted data.

`RAG_RETRIEVAL_TOP_K=5` is the default (1–`RAG_RETRIEVAL_MAX_TOP_K`); `RAG_RETRIEVAL_MAX_TOP_K=20` accepts 1–100. The active embedding profile selects `RAG_RETRIEVAL_OPENAI_SIMILARITY_THRESHOLD=0.60` or `RAG_RETRIEVAL_OLLAMA_SIMILARITY_THRESHOLD=0.70` (both 0–1), regardless of chat provider. An explicit retrieval request threshold overrides that default. These provisional values come from a small fictional corpus and require recalibration on a larger representative dataset. Validation applies only when RAG is enabled. Result metrics contain nullable query input tokens, provider-reported embedding latency, monotonic database search latency, and total service latency. No HNSW or IVFFlat index is present; see [ADR-006](../adr/ADR-006-exact-rag-retrieval.md).

## Grounded Incident analysis

When RAG is enabled and an Incident has an explicit KnowledgeBase, analysis retrieves profile-compatible chunks from that base before chat generation. The backend selects distinct chunks in retrieval order, up to `RAG_MAX_CONTEXT_CHUNKS=5` and `RAG_MAX_CONTEXT_CHARS=6000` UTF-16 content units. An oversized chunk is skipped without splitting; later chunks remain eligible. The selected content is untrusted JSON data in a separate user message. Documents are not system instructions. The model returns only source IDs; the backend validates those IDs against chunks actually sent and supplies citation metadata. Citations are source references, not proof that a document or analysis is correct. An empty citation list is allowed. Retrieval failure falls back to ordinary analysis with `RETRIEVAL_FAILED`; chat 502/504 behavior is unchanged. Retrieval may increase latency and cost, and embedding tokens are distinct from generation tokens. Analysis is not saved. Provider-specific similarity thresholds still require evaluation. See [ADR-008](../adr/ADR-008-grounded-incident-analysis.md).
# Evaluation

The Phase 4 evaluation corpus exists only in test resources and is ingested
through the normal service. Offline tests exercise isolation and citation
boundaries without model calls. An explicitly tagged live harness can compare
OpenAI and Ollama retrieval, sweep thresholds from one embedding per query,
and run one grounded incident smoke scenario. Reports are stored under
`target/rag-evaluation/`; see [evaluation procedure](../evaluation/rag-evaluation.md).
The embedding-provider defaults are OpenAI 0.60 and Ollama 0.70. Both are
provisional and need calibration on a larger representative corpus. The
recorded OpenAI smoke run supplied one chunk and one validated citation;
this is integration evidence, not a quality benchmark.
