package dev.alexey.devassist.analysis.exception;

import dev.alexey.devassist.analysis.IncidentAnalysisAttemptUsage;

import java.util.List;

/** Contains only validator-generated field paths and fixed messages, never rejected values or causes. */
public final class IncidentAnalysisValidationException extends IncidentAnalysisException {
	private final IncidentAnalysisAttemptUsage usage;

	public IncidentAnalysisAttemptUsage usage() {
		return usage;
	}

	private final List<String> violations;

	public IncidentAnalysisValidationException(List<String> violations) {
		this(violations, IncidentAnalysisAttemptUsage.UNKNOWN);
	}

	public IncidentAnalysisValidationException(List<String> violations, IncidentAnalysisAttemptUsage usage) {
		super("Incident analysis validation failed.");
		this.violations = violations.stream().sorted().toList();
		this.usage = usage;
	}

	public List<String> violations() {
		return violations;
	}
}
