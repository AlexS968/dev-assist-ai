package dev.alexey.devassist.analysis.exception;

public class EmptyIncidentAnalysisException extends IncidentAnalysisException {

	public EmptyIncidentAnalysisException() {
		super("Incident analysis provider returned empty content.");
	}
}
