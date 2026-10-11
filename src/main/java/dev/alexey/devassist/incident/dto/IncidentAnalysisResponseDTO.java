package dev.alexey.devassist.incident.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import dev.alexey.devassist.analysis.grounding.IncidentGroundingStatus;

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
		Integer totalTokens,
		@Schema(description = "Number of attempts: 1 for initial success, 2 after one repair", example = "1", minimum = "1", maximum = "2")
		int attemptCount,
        @Schema(description = "Reason grounding context was or was not provided")
        IncidentGroundingStatus groundingStatus,
        @Schema(description = "True only when at least one validated source was cited")
        boolean grounded,
        @Schema(description = "Explicitly associated KnowledgeBase, if any", nullable = true) UUID knowledgeBaseId,
        @Schema(description = "Backend-validated references to sources actually provided to the model")
        List<IncidentAnalysisCitationDTO> citations,
        @Schema(description = "Retrieval duration in milliseconds; zero when retrieval was skipped")
        long retrievalLatencyMs,
        @Schema(description = "Query embedding tokens; null when unavailable or retrieval was skipped", nullable = true)
        Integer retrievalInputTokens,
        @Schema(description = "Chunks returned by retrieval before context limits")
        int retrievedChunkCount,
        @Schema(description = "Distinct chunks actually provided to the model")
        int providedContextChunkCount,
        @Schema(description = "UTF-16 content code units actually provided")
        int providedContextCharacters) {
    public IncidentAnalysisResponseDTO {
        citations = List.copyOf(citations);
    }
}
