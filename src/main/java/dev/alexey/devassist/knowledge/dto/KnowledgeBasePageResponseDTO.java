package dev.alexey.devassist.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Stable paginated KnowledgeBase response")
public record KnowledgeBasePageResponseDTO(List<KnowledgeBaseResponseDTO> items,
        int page, int size, long totalElements, int totalPages) {
    public KnowledgeBasePageResponseDTO {
        items = List.copyOf(items);
    }
}
