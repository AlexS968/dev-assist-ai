package dev.alexey.devassist.incident.dto;

import java.util.List;

public record IncidentPageResponseDTO(
		List<IncidentResponseDTO> items,
		int page,
		int size,
		long totalElements,
		int totalPages) {
}
