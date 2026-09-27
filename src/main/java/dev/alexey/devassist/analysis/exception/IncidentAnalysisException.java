package dev.alexey.devassist.analysis.exception;

/** Application error; provider details are retained only as an internal cause. */
public class IncidentAnalysisException extends RuntimeException {

	public IncidentAnalysisException(Throwable cause) {
		super("Incident analysis provider failed.", cause);
	}

	protected IncidentAnalysisException(String message) {
		super(message);
	}
}
