package dev.alexey.devassist.incident.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Stable paginated incident response")
public record IncidentPageResponseDTO(
		@Schema(description = "Incidents on the current page, ordered by createdAt DESC and id DESC")
		List<IncidentResponseDTO> items,
		@Schema(description = "Zero-based current page index", example = "0")
		int page,
		@Schema(description = "Requested page size", example = "20")
		int size,
		@Schema(description = "Total number of incidents", example = "42")
		long totalElements,
		@Schema(description = "Total number of pages", example = "3")
		int totalPages) {
}
