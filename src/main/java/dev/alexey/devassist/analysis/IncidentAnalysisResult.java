package dev.alexey.devassist.analysis;

import java.time.Instant;
import dev.alexey.devassist.analysis.model.StructuredIncidentAnalysis;

/** Provider-neutral analysis. Null token counts mean usage was not reported. */
public record IncidentAnalysisResult(StructuredIncidentAnalysis analysis, String provider, String model, String promptVersion,
		Instant generatedAt, long latencyMs, Integer inputTokens, Integer outputTokens,
		Integer totalTokens) {
}
