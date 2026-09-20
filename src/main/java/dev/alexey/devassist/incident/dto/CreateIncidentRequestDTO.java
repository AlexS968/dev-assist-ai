package dev.alexey.devassist.incident.dto;

import dev.alexey.devassist.incident.entity.IncidentSource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIncidentRequestDTO(
		@NotBlank @Size(max = 200) String title,
		@NotBlank String description,
		@NotNull IncidentSource source) {
}
