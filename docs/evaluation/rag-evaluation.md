# Phase 4 RAG evaluation

The fictional Payment Service and Order Service corpus lives only under
`src/test/resources/rag-evaluation/`. It is loaded through
`KnowledgeDocumentIngestionService`, including normalisation and chunking.
It contains no production seed data or credentials. Payment has a database
pool runbook, API architecture note, unrelated frontend authentication guide,
and an untrusted operations note containing `EVALUATION_INJECTION_MARKER`.
Order has two PostgreSQL/HikariCP notes marked `ORDER_SERVICE_ONLY`.

## Offline checks

`./mvnw -o clean verify` runs deterministic corpus, ingestion, isolation,
context budget and citation checks with PostgreSQL Testcontainers and no AI
network calls. The live tests carry JUnit tag `live-rag`; default Surefire
excludes it. CI does not activate the live Maven profile.

## Explicit live run

Use a disposable Testcontainers database. No local development database or
Docker volume is touched. Run only after installing the chosen model and
setting all environment variables. Do not put credentials in command lines,
Git, reports, or logs.

OpenAI:

```sh
export AI_PROVIDER=openai RAG_ENABLED=true RAG_EMBEDDING_PROVIDER=openai
export OPENAI_MODEL=gpt-6-luna OPENAI_EMBEDDING_MODEL=text-embedding-3-small
export RAG_EMBEDDING_DIMENSIONS=768 AI_PROMPT_VERSION=incident-analysis-v3
export AI_MAX_OUTPUT_TOKENS=1000
# Set OPENAI_API_KEY securely in the environment.
RAG_LIVE_PROVIDER=openai ./mvnw -Prag-live-evaluation test
```

Ollama prerequisites (run manually): `ollama pull qwen3:14b` and
`ollama pull nomic-embed-text`. Start Ollama separately.

```sh
export AI_PROVIDER=ollama RAG_ENABLED=true RAG_EMBEDDING_PROVIDER=ollama
export OLLAMA_BASE_URL=http://localhost:11434 OLLAMA_MODEL=qwen3:14b
export OLLAMA_EMBEDDING_MODEL=nomic-embed-text RAG_EMBEDDING_DIMENSIONS=768
export AI_PROMPT_VERSION=incident-analysis-v3 AI_MAX_OUTPUT_TOKENS=1000
export AI_TIMEOUT=60s RAG_EMBEDDING_TIMEOUT=60s
RAG_LIVE_PROVIDER=ollama ./mvnw -Prag-live-evaluation test
```

The selected provider is explicit; the harness does not infer it from a key
or a running service. Live OpenAI calls can incur cost. Both providers are
nondeterministic. A single run is a smoke observation, not a benchmark.

## Method

Each scenario runs one query embedding and exact PostgreSQL cosine retrieval
with threshold 0.0 and topK equal to the configured maximum. The report
reuses those ranked scores locally for thresholds 0.50, 0.55, 0.60, 0.65,
0.70, 0.75, 0.80 and 0.85. It never makes eight embedding calls per query.
The production default remains 0.70 pending a larger evaluation corpus.

Hit@K is 1 if at least one expected external ID appears in the first K
results, otherwise 0. Recall@K is the number of distinct expected external
IDs in the first K divided by the number of expected IDs. A scenario with no
expected IDs contributes 0 to recall and may validly be empty. Reciprocal
rank is 1 divided by the rank of the first expected result, or 0 if absent;
MRR is its mean over scenarios. Threshold rows report relevant hits,
forbidden hits, empty scenarios, Hit@1, Hit@3, Hit@K and mean Recall@K.

Cross-KnowledgeBase results, wrong embedding profiles, invalid citations,
schema failures after repair, infrastructure failure and database corruption
are hard failures. Low rank, low similarity, generic answers and a
suboptimal threshold are quality observations in the report. The live smoke
tests a Payment incident with a linked KnowledgeBase and backend-validated
citations. A citation identifies a supplied source; it does not prove that
the document or analysis is correct. The injected note remains untrusted
source content. The structural prompt boundary reduces risk but cannot
guarantee resistance to prompt injection. If a model mentions the injection
as evidence, that alone is not obedience to it.

Reports are written only to ignored `target/rag-evaluation/openai-report.json`
or `target/rag-evaluation/ollama-report.json`. They contain configuration
without secrets, corpus SHA-256 hashes, scenario IDs, ranked external/chunk
IDs and scores, threshold metrics, latency and token counts, smoke status,
attempt count and citation metadata. They omit content, vectors, raw model
responses, prompt text, API keys and full incident descriptions.
