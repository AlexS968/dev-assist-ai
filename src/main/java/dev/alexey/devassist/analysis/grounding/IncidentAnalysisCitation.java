package dev.alexey.devassist.analysis.grounding;

import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import java.util.UUID;

public record IncidentAnalysisCitation(String sourceId, UUID knowledgeBaseId, UUID documentId,
        String externalId, String documentTitle, KnowledgeSourceType sourceType, String sourceUri,
        UUID chunkId, int chunkIndex, double similarity) {}
