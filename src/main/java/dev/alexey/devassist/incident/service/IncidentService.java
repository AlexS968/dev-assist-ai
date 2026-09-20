package dev.alexey.devassist.incident.service;

import dev.alexey.devassist.incident.entity.Incident;
import dev.alexey.devassist.incident.enums.IncidentSource;
import dev.alexey.devassist.incident.enums.IncidentStatus;
import dev.alexey.devassist.incident.exception.IncidentNotFoundException;
import dev.alexey.devassist.incident.exception.InvalidIncidentStatusTransitionException;
import dev.alexey.devassist.incident.repository.IncidentRepository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {

	private final IncidentRepository repository;

	public IncidentService(IncidentRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public Incident create(String title, String description, IncidentSource source) {
		return repository.save(new Incident(title, description, source));
	}

	@Transactional(readOnly = true)
	public Incident findById(UUID id) {
		return repository.findById(id).orElseThrow(() -> new IncidentNotFoundException(id));
	}

	@Transactional(readOnly = true)
	public Page<Incident> findAll(int page, int size) {
		return repository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
	}

	@Transactional
	public Incident updateStatus(UUID id, IncidentStatus status) {
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
		return incident;
	}
}
