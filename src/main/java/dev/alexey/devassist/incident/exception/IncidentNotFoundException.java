package dev.alexey.devassist.incident.exception;

import dev.alexey.devassist.incident.entity.Incident;

import java.util.UUID;

public class IncidentNotFoundException extends RuntimeException {

	public IncidentNotFoundException(UUID id) {
		super("Incident with id " + id + " was not found.");
	}
}
