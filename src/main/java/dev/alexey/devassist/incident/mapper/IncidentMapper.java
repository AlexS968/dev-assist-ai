package dev.alexey.devassist.incident.mapper;

import dev.alexey.devassist.incident.dto.IncidentResponseDTO;
import dev.alexey.devassist.incident.dto.IncidentPageResponseDTO;
import dev.alexey.devassist.incident.entity.Incident;

import org.springframework.data.domain.Page;

public final class IncidentMapper {

	private IncidentMapper() {
	}

	public static IncidentResponseDTO toResponse(Incident incident) {
		return new IncidentResponseDTO(incident.getId(), incident.getTitle(), incident.getDescription(),
				incident.getStatus(), incident.getSource(), incident.getCreatedAt(), incident.getUpdatedAt());
	}

	public static IncidentPageResponseDTO toPageResponse(Page<Incident> incidents) {
		return new IncidentPageResponseDTO(incidents.getContent().stream().map(IncidentMapper::toResponse).toList(),
				incidents.getNumber(), incidents.getSize(), incidents.getTotalElements(), incidents.getTotalPages());
	}
}
