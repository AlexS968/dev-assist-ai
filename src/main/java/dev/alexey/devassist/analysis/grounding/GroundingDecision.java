package dev.alexey.devassist.analysis.grounding;

import java.util.UUID;

public record GroundingDecision(IncidentGroundingStatus status, UUID knowledgeBaseId,
        IncidentAnalysisContext context, long retrievalLatencyMs, Integer retrievalInputTokens,
        int retrievedChunkCount) {
    public static GroundingDecision withoutRetrieval(IncidentGroundingStatus status, UUID knowledgeBaseId) {
        return new GroundingDecision(status, knowledgeBaseId, IncidentAnalysisContext.EMPTY, 0, null, 0);
    }

    public int providedContextChunkCount() {
        return context.sources().size();
    }

    public int providedContextCharacters() {
        return context.characterCount();
    }
}
