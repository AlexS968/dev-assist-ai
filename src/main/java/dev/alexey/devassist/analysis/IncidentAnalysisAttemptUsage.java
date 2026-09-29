package dev.alexey.devassist.analysis;

/** Safe internal attempt telemetry; contains no incident, prompt or response data. */
public record IncidentAnalysisAttemptUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
	public static final IncidentAnalysisAttemptUsage UNKNOWN = new IncidentAnalysisAttemptUsage(null, null, null);
}
