package dev.alexey.devassist.knowledge.embedding;

import java.util.List;

public record KnowledgeEmbeddingRequest(List<String> texts, EmbeddingPurpose purpose) {
    public KnowledgeEmbeddingRequest {
        if (texts == null || texts.isEmpty() || texts.stream().anyMatch(text -> text == null || text.isBlank())) {
            throw new IllegalArgumentException("Embedding input must contain nonblank texts");
        }
        texts = List.copyOf(texts);
        if (purpose == null) throw new IllegalArgumentException("Embedding purpose is required");
    }
}
