package dev.alexey.devassist.analysis.exception;

/** Intentionally retains no Jackson cause, rejected value or raw model response. */
public final class IncidentAnalysisConversionException extends IncidentAnalysisException {
	public IncidentAnalysisConversionException() {
		super("Incident analysis response could not be converted.");
	}
}
