package dev.alexey.devassist.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "KnowledgeBase metadata; documents and chunks are excluded")
public record KnowledgeBaseResponseDTO(UUID id, String key, String name, String description,
        Instant createdAt, Instant updatedAt) {}
