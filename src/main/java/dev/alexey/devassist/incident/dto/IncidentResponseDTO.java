package dev.alexey.devassist.incident.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import dev.alexey.devassist.incident.enums.IncidentSource;
import dev.alexey.devassist.incident.enums.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Incident details")
public record IncidentResponseDTO(
		@Schema(description = "Unique incident identifier", example = "550e8400-e29b-41d4-a716-446655440000")
		UUID id,
		@Schema(description = "Short incident title", example = "Database connection timeout")
		String title,
		@Schema(description = "Incident description", example = "Checkout requests fail with database connection timeouts.")
		String description,
		@Schema(description = "Current incident status: NEW (reported), IN_PROGRESS (investigation started), RESOLVED (completed)",
				example = "NEW", allowableValues = {"NEW", "IN_PROGRESS", "RESOLVED"})
		IncidentStatus status,
		@Schema(description = "Report origin: MANUAL (human entry), API (external client), MONITORING (monitoring system)",
				example = "MANUAL", allowableValues = {"MANUAL", "API", "MONITORING"})
		IncidentSource source,
		@Schema(description = "Creation timestamp in UTC", example = "2026-09-20T10:00:00Z")
		Instant createdAt,
		@Schema(description = "Last modification timestamp in UTC", example = "2026-09-20T10:15:00Z")
		Instant updatedAt) {
}
