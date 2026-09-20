package dev.alexey.devassist.incident.service;

import dev.alexey.devassist.incident.entity.Incident;
import dev.alexey.devassist.incident.entity.IncidentSource;
import dev.alexey.devassist.incident.exception.IncidentNotFoundException;
import dev.alexey.devassist.incident.repository.IncidentRepository;

import java.util.UUID;

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
}
