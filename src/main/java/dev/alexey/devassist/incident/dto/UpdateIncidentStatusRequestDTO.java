package dev.alexey.devassist.incident.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import dev.alexey.devassist.incident.enums.IncidentStatus;

import jakarta.validation.constraints.NotNull;

@Schema(description = "Requested next status; only NEW -> IN_PROGRESS and IN_PROGRESS -> RESOLVED are allowed")
public record UpdateIncidentStatusRequestDTO(
		@Schema(description = "Next incident status: NEW (reported), IN_PROGRESS (investigation started), RESOLVED (completed)",
				example = "IN_PROGRESS", allowableValues = {"NEW", "IN_PROGRESS", "RESOLVED"})
		@NotNull IncidentStatus status) {
}
