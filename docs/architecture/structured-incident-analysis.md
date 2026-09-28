# Phase 3, step 1: structured incident analysis contract

This step adds an independent candidate model and explicit Java validation boundary.
The gateway, provider implementations, v1 prompts, result metadata, mapper and public
REST response still use plain text. No JSON parsing, schema generation, retry,
repair or provider call is introduced. ADR-003 remains **Proposed**: providers do
not yet return schema-validated structured output.

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

## Example future JSON payload

This is the proposed analysis body, not the current REST response or a parser implementation.

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

## Before migrating OpenAI and Ollama

- Introduce a new prompt version and provider-specific structured-output configuration;
  verify both adapters against the same contract with mocked responses.
- Validate every converted candidate before returning it. Keep parse/conversion errors
  sanitized as well; never attach raw output or framework violations containing values.
- Decide JSON handling for missing/null/non-integer `order`, unknown enum values and
  unknown fields. Java `int` only represents integers; a parser must reject coercion.
- Confirm schema constraints and output-budget behavior separately for each provider.
  The current 450-token budget may truncate structured output; no budget changes
  or live experiments are part of this step.
- Plan gateway/result/REST migration and compatibility separately. No automatic
  retry, repair or fallback is implied by this contract.
