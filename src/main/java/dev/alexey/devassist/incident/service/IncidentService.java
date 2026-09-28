package dev.alexey.devassist.incident.service;

import dev.alexey.devassist.incident.dto.IncidentResponseDTO;
import dev.alexey.devassist.incident.dto.IncidentPageResponseDTO;
import dev.alexey.devassist.incident.mapper.IncidentMapper;

import dev.alexey.devassist.incident.entity.Incident;
import dev.alexey.devassist.incident.enums.IncidentSource;
import dev.alexey.devassist.incident.enums.IncidentStatus;
import dev.alexey.devassist.incident.exception.IncidentNotFoundException;
import dev.alexey.devassist.incident.exception.InvalidIncidentStatusTransitionException;
import dev.alexey.devassist.incident.repository.IncidentRepository;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {

	private final IncidentRepository repository;
	private final IncidentMapper mapper;

	public IncidentService(IncidentRepository repository, IncidentMapper mapper) {
		this.repository = repository;
		this.mapper = mapper;
	}

	@Transactional
	public IncidentResponseDTO create(String title, String description, IncidentSource source) {
		return mapper.toResponse(repository.save(new Incident(title, description, source)));
	}

	@Transactional(readOnly = true)
	public IncidentResponseDTO findById(UUID id) {
		return mapper.toResponse(repository.findById(id).orElseThrow(() -> new IncidentNotFoundException(id)));
	}

	@Transactional(readOnly = true)
	public IncidentPageResponseDTO findAll(int page, int size) {
		return mapper.toPageResponse(repository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"))));
	}

	@Transactional
	public IncidentResponseDTO updateStatus(UUID id, IncidentStatus status) {
		Incident incident = repository.findById(id).orElseThrow(() -> new IncidentNotFoundException(id));
		boolean allowed = switch (incident.getStatus()) {
			case NEW -> status == IncidentStatus.IN_PROGRESS;
			case IN_PROGRESS -> status == IncidentStatus.RESOLVED;
			case RESOLVED -> false;
		};
		if (!allowed) {
			throw new InvalidIncidentStatusTransitionException(incident.getStatus(), status);
		}
		incident.setStatus(status);
		// Apply @PreUpdate before taking the response snapshot.
		repository.flush();
		return mapper.toResponse(incident);
	}
}
