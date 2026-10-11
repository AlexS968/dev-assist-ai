# ADR-007: Explicit optional Incident–KnowledgeBase association

Status: Accepted

## Context

A KnowledgeBase holds documentation for one project or service. Incident title and description are insufficient to select its documentation reliably. New installations may have no KnowledgeBase, and an Incident must remain useful before documentation is configured.

## Decision

The client explicitly supplies an optional KnowledgeBase UUID when creating an Incident or later changes it with a dedicated PATCH. No LLM or heuristic selects a base. An Incident belongs to at most one base; one base may be linked to many Incidents. The nullable foreign key works independently of `RAG_ENABLED`, so ordinary analysis continues without a base or when RAG is disabled. Linking a base does not invoke retrieval during analysis or change the structured response.

Flyway V5 adds `incidents.knowledge_base_id` with `ON DELETE SET NULL`. Deleting a base preserves Incidents but clears their association; its documents and chunks continue to cascade-delete. Repeating an assignment or clearing an already-null association is a no-op and preserves `updatedAt`. The minimal public KnowledgeBase API creates and reads empty bases but does not expose deletion: authorization and audit behavior require a separate decision.

## Consequences

Users must choose a base manually, which adds work but prevents silent cross-project context mixing. A greenfield Incident can be analyzed without any base. Future project or tenant authorization must constrain which bases a caller can associate with an Incident. Grounded generation and citations remain separate work.
