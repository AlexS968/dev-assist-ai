package dev.alexey.devassist.incident.dto;

import dev.alexey.devassist.incident.enums.IncidentSource;
import dev.alexey.devassist.incident.enums.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

public record IncidentResponseDTO(
		UUID id,
		String title,
		String description,
		IncidentStatus status,
		IncidentSource source,
		Instant createdAt,
		Instant updatedAt) {
}
