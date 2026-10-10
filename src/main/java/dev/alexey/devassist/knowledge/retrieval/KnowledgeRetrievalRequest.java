package dev.alexey.devassist.knowledge.retrieval;

import java.util.UUID;

public record KnowledgeRetrievalRequest(UUID knowledgeBaseId, String query,
        Integer topK, Double similarityThreshold) {
    public KnowledgeRetrievalRequest {
        if (knowledgeBaseId == null || query == null || query.isBlank() || query.length() > 10000) {
            throw new IllegalArgumentException("KnowledgeBase ID and nonblank query of at most 10000 characters are required");
        }
        if (topK != null && topK < 1) throw new IllegalArgumentException("Retrieval topK must be positive");
        if (similarityThreshold != null && (!Double.isFinite(similarityThreshold)
                || similarityThreshold < 0 || similarityThreshold > 1)) {
            throw new IllegalArgumentException("Retrieval similarity threshold must be between 0 and 1");
        }
    }
}
