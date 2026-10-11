package dev.alexey.devassist.knowledge.embedding;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record KnowledgeEmbeddingResult(List<KnowledgeEmbeddingVector> vectors, String provider,
        String model, int dimensions, Integer inputTokens, Duration latency) {
    public KnowledgeEmbeddingResult {
        if (vectors == null || vectors.isEmpty() || vectors.stream().anyMatch(Objects::isNull)
                || provider == null || provider.isBlank() || model == null || model.isBlank()
                || dimensions != 768 || latency == null || latency.isNegative()
                || (inputTokens != null && inputTokens < 0)) {
            throw new IllegalArgumentException("Invalid embedding result");
        }
        vectors = List.copyOf(vectors);
    }
}
