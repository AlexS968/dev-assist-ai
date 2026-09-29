# Phase 3: structured incident analysis contract

Both adapters now request native structured output using one canonical resource,
`schemas/incident-analysis-v2.json`. The gateway result and REST response carry
`analysis` plus unchanged provider/model/promptVersion/timing/nullable token metadata.
The old plain-text `content` field is removed. Controller still delegates only to
service; service uses MapStruct to map to separate REST DTO records with Swagger
annotations. Core records remain independent of Jackson and Swagger.
ADR-003 remains **Proposed** until live structured smoke tests for both providers.
No live calls were made for this migration.

## Contract

Records in `analysis.model`: `StructuredIncidentAnalysis`, `ProbableCause`,
`InvestigationStep`. `Likelihood` lives in `analysis.enums`.

| Field | Constraint |
|---|---|
| summary | Required, nonblank, at most 500 characters |
| probableCauses | Required, 1–3 nonnull causes |
| cause.title | Required, nonblank, at most 120 characters |
| cause.explanation | Required, nonblank, at most 500 characters |
| cause.likelihood | Required: LOW, MEDIUM, HIGH |
| cause.evidenceToCheck | Required, 1–3 nonblank strings, each at most 250 characters |
| investigationSteps | Required, 1–4 nonnull steps |
| step.order | Integer, unique, equal to one-based list position: 1..N |
| step.action | Required, nonblank, at most 300 characters |
| step.rationale | Required, nonblank, at most 300 characters |
| uncertainties | Required, 1–3 nonblank strings, each at most 250 characters |

`likelihood` is qualitative model prioritization of hypotheses. It is neither a
probability nor measured/calibrated confidence; HIGH does not establish truth.
There is deliberately no `rootCause`: one incident report cannot establish a
verified root cause. Required `uncertainties` make missing evidence and assumptions
explicit. Structural validation cannot verify factual accuracy or meaningful uncertainty.

## Validation boundary

Call `new IncidentAnalysisValidator().validate(candidate)` before trusting a candidate.
The stateless validator traverses the complete graph, including elements of lists
whose size is already invalid. It collects all errors and throws
`IncidentAnalysisValidationException`, a distinct application exception.
`violations()` is an immutable list sorted lexicographically by field path/message;
paths use zero-based list indices. No Set iteration determines diagnostic order.
Messages are fixed application text and contain no rejected values. The exception
has a generic message and no cause or candidate reference. Do not log candidate
records: their generated `toString()` contains model data.

Constructors take defensive copies of every list and expose unmodifiable lists.
Null lists and null elements are deliberately preserved until explicit validation,
so construction does not fail with an incidental NullPointerException. A constructed
record is therefore an immutable candidate, not proof of validity. Strings are not
trimmed, normalized or truncated. Blank uses Java `String.isBlank()` and length
uses Java `String.length()` (UTF-16 code units).

## JSON analysis payload

This object is nested under `analysis` in the REST response. See README for the complete envelope.

```json
{
  "summary": "Database requests are timing out; connection pressure is a hypothesis.",
  "probableCauses": [
    {
      "title": "Connection pool exhaustion",
      "explanation": "Long-running queries may be keeping connections occupied.",
      "likelihood": "MEDIUM",
      "evidenceToCheck": ["Compare active connections and wait time with pool limits."]
    }
  ],
  "investigationSteps": [
    {
      "order": 1,
      "action": "Inspect pool metrics during the incident window.",
      "rationale": "Sustained saturation would support the connection pressure hypothesis."
    }
  ],
  "uncertainties": ["Query duration metrics and database logs have not been provided."]
}
```

## Native enforcement and conversion

OpenAI uses Spring AI 2.0.1 `OpenAiChatModel.ResponseFormat` with `JSON_SCHEMA`,
the canonical schema string and `strict=true`. Spring AI converts this to the
official client's Chat Completions `response_format.json_schema` request.
Ollama uses `OllamaApi.ChatRequest.format` with the same schema as a JSON object;
`think=false`, `num_predict`, transport timeouts and no-retry behavior are preserved.
The schema requires all fields, describes types and enum values, and sets
`additionalProperties=false` on every object. To keep the provider schema small
and portable, sizes, nonblank strings and consecutive orders are enforced by the
application validator (and explained in v2 prompts), not by schema keywords.

`IncidentAnalysisConverter` owns a dedicated Jackson 3 ObjectReader; the REST mapper
is untouched. It rejects malformed JSON, comments/fences, trailing tokens, duplicate
keys, unknown properties, invalid enum values, missing/null creator fields and scalar
coercion (including string/fractional order values and numeric enum ordinals).
No regex extraction or cleanup is attempted. Every parsed candidate, including a
JSON null root, goes through `IncidentAnalysisValidator` before a result is returned.
Parsing errors become `IncidentAnalysisConversionException` without the Jackson
cause or raw content. Semantic failures remain `IncidentAnalysisValidationException`.
Both extend `IncidentAnalysisException` and return the existing fixed 502 ProblemDetail;
transport timeouts remain 504 and unknown incident IDs remain 404. No new logging
of responses, incident data or prompts is introduced. Empty output retains its
existing safe empty-response exception and 502 mapping.

Only `incident-analysis-v2` is accepted at startup; v1 resources are preserved but
incompatible with this runtime. System/user roles remain separate. Incident data
appears only in the user message. The prompt requests concise findings, not internal
reasoning traces. The default output budget rises from 450 to 1000 tokens, reducing
truncation risk at potentially higher cost/latency; overrides remain supported.
The maximum contract size can still exceed that budget. No retry, repair or fallback
is implemented.

## Before live smoke tests

Offline tests verify native request options, strict conversion, validation, REST
mapping and safe errors, not model quality or actual provider enforcement. Test v2
with each configured model and provider version, including output limits, refusal,
truncation and useful uncertainties. A configured model override must support native
schema output; provider rejection remains a safe failure, never prompt-only fallback.
No schema can prove an incident's root cause or calibrate model likelihood.

Sources checked against local Spring AI 2.0.1 source jars:
[OpenAI structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs),
[GPT-6 Luna capabilities](https://developers.openai.com/api/docs/models/gpt-6-luna),
[Spring AI OpenAI](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html),
[Ollama structured outputs](https://docs.ollama.com/capabilities/structured-outputs).
