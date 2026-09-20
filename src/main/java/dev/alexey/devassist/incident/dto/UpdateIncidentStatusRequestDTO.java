package dev.alexey.devassist.incident.dto;

import dev.alexey.devassist.incident.enums.IncidentStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateIncidentStatusRequestDTO(@NotNull IncidentStatus status) {
}
