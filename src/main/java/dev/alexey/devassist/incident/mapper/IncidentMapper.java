package dev.alexey.devassist.incident.mapper;

import dev.alexey.devassist.incident.dto.IncidentResponseDTO;
import dev.alexey.devassist.incident.entity.Incident;

public final class IncidentMapper {

	private IncidentMapper() {
	}

	public static IncidentResponseDTO toResponse(Incident incident) {
		return new IncidentResponseDTO(incident.getId(), incident.getTitle(), incident.getDescription(),
				incident.getStatus(), incident.getSource(), incident.getCreatedAt(), incident.getUpdatedAt());
	}
}
