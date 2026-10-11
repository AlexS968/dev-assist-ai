package dev.alexey.devassist.analysis.grounding;

import dev.alexey.devassist.knowledge.retrieval.RetrievedKnowledgeChunk;

public record IncidentAnalysisSource(String sourceId, RetrievedKnowledgeChunk chunk) {}
