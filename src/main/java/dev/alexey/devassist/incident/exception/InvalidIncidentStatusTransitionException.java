package dev.alexey.devassist.incident.exception;

import dev.alexey.devassist.incident.enums.IncidentStatus;

public class InvalidIncidentStatusTransitionException extends RuntimeException {

	public InvalidIncidentStatusTransitionException(IncidentStatus current, IncidentStatus requested) {
		super("Cannot change incident status from " + current + " to " + requested
				+ ". Allowed transitions: NEW -> IN_PROGRESS, IN_PROGRESS -> RESOLVED.");
	}
}
