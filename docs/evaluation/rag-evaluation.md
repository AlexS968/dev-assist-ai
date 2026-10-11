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
Docker volume is touched. `RAG_LIVE_PROVIDER` is the only required selector.
The harness sets RAG enabled, provider selection, v3 prompt, 768 dimensions,
and 1000 output tokens in test-only configuration. It defaults both timeouts
to 60s while respecting `AI_TIMEOUT` and `RAG_EMBEDDING_TIMEOUT` when set.
The normal application settings still honor explicit model overrides and
`OLLAMA_BASE_URL`. Do not put credentials in Git, reports, or logs.
For OpenAI live evaluation, test-only configuration replaces the offline
fake key and loopback URL with `OPENAI_API_KEY` and the standard
`https://api.openai.com/v1` endpoint for both chat and embeddings. The
offline test defaults remain unchanged.

OpenAI:

```sh
# Set OPENAI_API_KEY securely in the environment before running this command.
RAG_LIVE_PROVIDER=openai ./mvnw -Prag-live-evaluation test
```

Ollama must be running. Install `qwen3:14b` and `nomic-embed-text` manually
with `ollama pull`; the harness never pulls them.

```sh
RAG_LIVE_PROVIDER=ollama ./mvnw -Prag-live-evaluation test
```

The selected provider is explicit; the harness does not infer it from a key
or a running service. Defaults are `gpt-6-luna` and
`text-embedding-3-small` for OpenAI, and `qwen3:14b` and
`nomic-embed-text` for Ollama. Override models with `OPENAI_MODEL`,
`OPENAI_EMBEDDING_MODEL`, `OLLAMA_MODEL`, or `OLLAMA_EMBEDDING_MODEL`;
override the Ollama URL with `OLLAMA_BASE_URL` (default
`http://localhost:11434`). PostgreSQL and the Spring Boot application need
no manual startup; Testcontainers and the test context provide them.
`Tests run: 0` is a configuration error, not a completed evaluation.
A successful run creates `target/rag-evaluation/{provider}-report.json`.
Live OpenAI calls can incur cost. Both providers are nondeterministic. A
single run is a smoke observation, not a benchmark.

## Method

Each scenario runs one query embedding and exact PostgreSQL cosine retrieval
with threshold 0.0 and topK equal to the configured maximum. The report
reuses those ranked scores locally for thresholds 0.50, 0.55, 0.60, 0.65,
0.70, 0.75, 0.80 and 0.85. It never makes eight embedding calls per query.
The provisional application defaults are 0.60 for OpenAI embeddings and 0.70
for Ollama embeddings. The active embedding provider selects the default even
when chat uses another provider; an explicit retrieval request can override it.
These values come only from this small fictional corpus and require
recalibration on a larger representative dataset.
Before grounded smoke, the harness also performs one diagnostic retrieval for
the smoke Incident's exact title and description at threshold 0.0. This adds
one query embedding call to a future live run. Its ranked external IDs, chunk
IDs and scores, the configured provider threshold, and the number of chunks
eligible for the production context budget are saved before the smoke
assertions. The ordinary scenario results and threshold sweep are also saved
before those assertions. The report contains no query or chunk text.

## Recorded OpenAI v3 live evaluation

The current `target/rag-evaluation/openai-report.json` records `status=SUCCESS`
with `incident-analysis-v3` and an effective OpenAI embedding threshold of
0.60. The grounded smoke returned `CONTEXT_PROVIDED` and `grounded=true`:
one chunk was retrieved and provided, and one backend-validated citation
`S1` referred to `payment-pool-runbook`. Its similarity was
`0.6974672971559257`. The smoke report records generation latency of
6203 ms, retrieval latency of 159 ms and `attemptCount=1`. These are
observations from one run, not benchmark measurements.

The retrieval evaluation used raw scenario results at threshold 0.0. Its
aggregate `forbiddenHits=3` counts forbidden documents in those raw results;
it is not the count at the configured 0.60 threshold. The report's local
threshold sweep is:

| Threshold | Relevant hits | Forbidden hits | Empty scenarios |
| --- | ---: | ---: | ---: |
| 0.50 | 4 | 0 | 1 |
| 0.55 | 4 | 0 | 1 |
| 0.60 | 4 | 0 | 1 |
| 0.65 | 3 | 0 | 2 |
| 0.70 | 2 | 0 | 3 |
| 0.75 | 1 | 0 | 5 |
| 0.80 | 0 | 0 | 6 |
| 0.85 | 0 | 0 | 6 |

An earlier OpenAI attempt at threshold 0.70 returned `NO_RELEVANT_CONTEXT`;
its report was not saved after an `AssertionError`. The later successful run
above is the current recorded result. It does not establish factual accuracy
or universal threshold quality.

## Previously documented Ollama v3 evaluation

The previously documented Ollama threshold sweep found one unrelated hit at
0.60 and none at 0.65 or 0.70. No Ollama live report is present in the current
workspace after the offline clean build, so no additional Ollama metrics or
grounded-smoke outcome are inferred here. The provisional Ollama default is
0.70. OpenAI and Ollama thresholds both need calibration on a larger
representative corpus.

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

For future runs, if evaluation fails after the test context starts, the harness writes a
`status=FAILED` report before rethrowing the failure. Its `failure` object
contains only provider, stage, fixed category, and, when safely available,
HTTP status and an allowlisted provider error code. The embedding gateway
never copies raw SDK messages, headers, response bodies, inputs or stack
traces into that report. A missing HTTP status or code means the installed
SDK did not expose a safe value; it does not identify a credential, access
or billing problem by itself. A failed grounded smoke assertion also writes
the observed grounding status, grounded flag, retrieved/provided chunk
counts and citation count when the analysis response was received. For
`NO_RELEVANT_CONTEXT`, it records the quality observation and threshold;
the report remains `FAILED` and does not claim successful grounding. The
smoke-specific retrieval probe records scores and context eligibility even
when the smoke assertion fails.
