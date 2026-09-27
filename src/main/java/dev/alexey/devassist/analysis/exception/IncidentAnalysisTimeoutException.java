package dev.alexey.devassist.analysis.exception;

/** Application-level timeout; no transport types cross the gateway boundary. */
public class IncidentAnalysisTimeoutException extends IncidentAnalysisException {

	public IncidentAnalysisTimeoutException(Throwable cause) {
		super("Incident analysis provider timed out.", cause);
	}
}
