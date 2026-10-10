package dev.alexey.devassist.incident.dto;

import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Backend-validated reference to a provided knowledge chunk")
public record IncidentAnalysisCitationDTO(String sourceId, UUID documentId, String externalId,
        String documentTitle, KnowledgeSourceType sourceType, String sourceUri,
        UUID chunkId, int chunkIndex, double similarity) {}
