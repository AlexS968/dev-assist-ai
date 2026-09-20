package dev.alexey.devassist.incident.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import dev.alexey.devassist.incident.enums.IncidentSource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Data required to create an incident; status is assigned NEW by the server")
public record CreateIncidentRequestDTO(
		@Schema(description = "Short incident title", example = "Database connection timeout")
		@NotBlank @Size(max = 200) String title,
		@Schema(description = "Non-blank description of the incident", example = "Checkout requests fail with database connection timeouts.")
		@NotBlank String description,
		@Schema(description = "Report origin: MANUAL (human entry), API (external client), MONITORING (monitoring system)",
				example = "MANUAL", allowableValues = {"MANUAL", "API", "MONITORING"})
		@NotNull IncidentSource source) {
}
