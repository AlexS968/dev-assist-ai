package dev.alexey.devassist.analysis.exception;

import java.util.List;

/** Contains only validator-generated field paths and fixed messages, never rejected values or causes. */
public final class IncidentAnalysisValidationException extends IncidentAnalysisException {
	private final List<String> violations;

	public IncidentAnalysisValidationException(List<String> violations) {
		super("Incident analysis validation failed.");
		this.violations = violations.stream().sorted().toList();
	}

	public List<String> violations() {
		return violations;
	}
}
