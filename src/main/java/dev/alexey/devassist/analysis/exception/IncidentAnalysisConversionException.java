package dev.alexey.devassist.analysis.exception;

import dev.alexey.devassist.analysis.IncidentAnalysisAttemptUsage;

/** Intentionally retains no Jackson cause, rejected value or raw model response. */
public final class IncidentAnalysisConversionException extends IncidentAnalysisException {
	private final IncidentAnalysisAttemptUsage usage;

	public IncidentAnalysisAttemptUsage usage() {
		return usage;
	}

	public IncidentAnalysisConversionException() {
		this(IncidentAnalysisAttemptUsage.UNKNOWN);
	}

	public IncidentAnalysisConversionException(IncidentAnalysisAttemptUsage usage) {
		super("Incident analysis response could not be converted.");
		this.usage = usage;
	}
}
