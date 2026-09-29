package dev.alexey.devassist.incident.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Generated incident analysis; not persisted")
public record IncidentAnalysisResponseDTO(
		@Schema(description = "Validated structured incident analysis", requiredMode = Schema.RequiredMode.REQUIRED)
		StructuredIncidentAnalysisDTO analysis,
		@Schema(description = "AI provider identifier", example = "openai")
		String provider,
		@Schema(description = "Model reported by the provider, or configured model when absent", example = "gpt-6-luna")
		String model,
		@Schema(description = "Stable identifier of the prompt templates used for this analysis", example = "incident-analysis-v2")
		String promptVersion,
		@Schema(description = "UTC time when the application received the analysis", example = "2026-09-27T12:00:00Z")
		Instant generatedAt,
		@Schema(description = "Elapsed model call time in milliseconds, measured with a monotonic clock", example = "1250")
		long latencyMs,
		@Schema(description = "Input tokens reported by the provider; null when unavailable", example = "120", nullable = true)
		Integer inputTokens,
		@Schema(description = "Output tokens reported by the provider; null when unavailable", example = "80", nullable = true)
		Integer outputTokens,
		@Schema(description = "Total tokens reported by the provider; null when unavailable", example = "200", nullable = true)
		Integer totalTokens) {
}
