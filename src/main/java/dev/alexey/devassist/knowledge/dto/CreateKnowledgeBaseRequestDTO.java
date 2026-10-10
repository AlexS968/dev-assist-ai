package dev.alexey.devassist.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Create an empty KnowledgeBase for one project or service")
public record CreateKnowledgeBaseRequestDTO(
        @Schema(description = "Stable lowercase machine-readable key", example = "checkout-service")
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "[a-z0-9]+(?:[a-z0-9-]*[a-z0-9])?", message = "must contain lowercase letters, digits and internal hyphens")
        String key,
        @Schema(description = "Display name", example = "Checkout Service")
        @NotBlank @Size(max = 255) String name,
        @Schema(description = "Optional description", nullable = true)
        @Size(max = 5000) String description) {
    public CreateKnowledgeBaseRequestDTO {
        if (key != null) key = key.trim();
        if (name != null) name = name.trim();
    }
}
