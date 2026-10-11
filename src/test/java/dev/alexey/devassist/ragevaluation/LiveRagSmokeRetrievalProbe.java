package dev.alexey.devassist.ragevaluation;

import dev.alexey.devassist.analysis.grounding.IncidentAnalysisContextSelector;
import dev.alexey.devassist.knowledge.retrieval.KnowledgeRetrievalResult;
import dev.alexey.devassist.knowledge.retrieval.RetrievedKnowledgeChunk;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Records only safe ranking data for the exact grounded smoke query. */
final class LiveRagSmokeRetrievalProbe {
    private LiveRagSmokeRetrievalProbe() {}

    static Map<String, Object> snapshot(KnowledgeRetrievalResult result, double threshold,
            int productionTopK, int maxChunks, int maxContextChars) {
        List<RetrievedKnowledgeChunk> eligible = result.matches().stream()
                .filter(chunk -> chunk.similarity() >= threshold)
                .limit(productionTopK).toList();
        var context = new IncidentAnalysisContextSelector().select(eligible, maxChunks, maxContextChars);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("similarityThreshold", threshold);
        fields.put("productionTopK", productionTopK);
        fields.put("rawMatches", result.matches().stream().map(chunk -> Map.of(
                "externalId", chunk.externalId(), "chunkId", chunk.chunkId(),
                "similarity", chunk.similarity())).toList());
        fields.put("matchesAboveThreshold", eligible.size());
        fields.put("contextEligibleChunkCount", context.sources().size());
        fields.put("contextEligibleCharacters", context.characterCount());
        fields.put("retrievalLatencyMs", result.totalLatencyMs());
        fields.put("queryInputTokens", result.inputTokens());
        return fields;
    }
}
