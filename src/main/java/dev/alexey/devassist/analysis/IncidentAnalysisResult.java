package dev.alexey.devassist.analysis;

import java.time.Instant;

/** Provider-neutral analysis. Null token counts mean usage was not reported. */
public record IncidentAnalysisResult(String content, String provider, String model, String promptVersion,
		Instant generatedAt, long latencyMs, Integer inputTokens, Integer outputTokens,
		Integer totalTokens) {
}
