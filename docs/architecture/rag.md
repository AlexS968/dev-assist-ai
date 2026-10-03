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

Chunks have no embeddings and are not used for retrieval. Still pending: KnowledgeBase REST management, Incident association, embeddings, vector column and index, similarity search, retrieval and fallback, prompt grounding, citations, prompt injection protection and RAG evaluation. Current incident analysis continues without RAG.

See [ADR-004](../adr/ADR-004-rag-storage-foundation.md).
