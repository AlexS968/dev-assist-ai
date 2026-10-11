# ADR-008: Grounded Incident analysis and backend-validated citations

Status: Accepted

## Decision

Incident analysis retrieves relevant chunks before chat generation only when RAG is enabled and the Incident has an explicitly assigned KnowledgeBase. Retrieval and chat are separate calls outside database transactions. An empty compatible corpus or no result above the similarity threshold uses ordinary analysis. Retrieval timeout, provider error or vector search error also falls back to ordinary analysis and reports `RETRIEVAL_FAILED`; chat errors retain their existing 502/504 contract. Retrieval is not retried and has no provider fallback.

The application selects at most five distinct chunks in retrieval order and at most 6000 UTF-16 content code units. It skips an oversized chunk and checks later chunks. It never splits content. Selected chunks receive backend IDs `S1`, `S2`, etc. Their content is serialized as untrusted JSON data in a separate user message, never interpolated into system rules. The `incident-analysis-v3` contract requires a top-level `citationIds` array. The model may return an empty array and may cite only supplied IDs. The backend rejects duplicate or unknown IDs, then resolves titles, URIs and identifiers from the actual selected chunks. A repair attempt receives the same context and source IDs without repeating retrieval or including the invalid raw response.

`groundingStatus` distinguishes disabled RAG, missing base, no relevant context, supplied context and retrieval failure. `grounded` is true only with a valid citation. A citation identifies a source used in the answer; it does not establish source correctness or factual correctness of the analysis. The analysis and citations are not persisted.

## Consequences

Retrieval adds embedding cost, token use and latency; embedding token usage is reported separately from generation usage. Sending selected document content to a configured chat provider has privacy implications. Prompt instructions and JSON data separation reduce prompt injection risk but cannot eliminate it. Source quality, relevance threshold 0.70, citation faithfulness and factual accuracy still need evaluation. No tool calling or agentic workflow is introduced.
