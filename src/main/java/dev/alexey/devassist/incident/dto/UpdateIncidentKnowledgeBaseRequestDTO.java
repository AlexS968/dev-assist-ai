package dev.alexey.devassist.incident.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Assign an existing KnowledgeBase or clear the association with null")
public record UpdateIncidentKnowledgeBaseRequestDTO(
        @Schema(description = "KnowledgeBase UUID, or null to clear", nullable = true)
        UUID knowledgeBaseId) {}
