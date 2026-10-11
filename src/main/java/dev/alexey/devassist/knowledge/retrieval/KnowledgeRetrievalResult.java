package dev.alexey.devassist.knowledge.retrieval;

import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import java.util.List;
import java.util.UUID;

public record KnowledgeRetrievalResult(UUID knowledgeBaseId, EmbeddingProfile activeProfile,
        List<RetrievedKnowledgeChunk> matches, Integer inputTokens, long embeddingLatencyMs,
        long searchLatencyMs, long totalLatencyMs) {
    public KnowledgeRetrievalResult {
        matches = List.copyOf(matches);
    }
}
