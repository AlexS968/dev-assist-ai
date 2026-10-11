package dev.alexey.devassist.knowledge.retrieval;

import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag.retrieval")
public record RetrievalProperties(int topK, int maxTopK, double openaiSimilarityThreshold,
        double ollamaSimilarityThreshold) {
    public RetrievalProperties {
        if (maxTopK < 1 || maxTopK > 100 || topK < 1 || topK > maxTopK) {
            throw new IllegalArgumentException("RAG retrieval topK must be between 1 and maxTopK, and maxTopK between 1 and 100");
        }
        validateThreshold(openaiSimilarityThreshold);
        validateThreshold(ollamaSimilarityThreshold);
    }

    public double thresholdFor(EmbeddingProfile profile) {
        return switch (profile.provider()) {
            case "openai" -> openaiSimilarityThreshold;
            case "ollama" -> ollamaSimilarityThreshold;
            default -> throw new IllegalArgumentException("Unsupported RAG embedding provider");
        };
    }

    private static void validateThreshold(double threshold) {
        if (!Double.isFinite(threshold) || threshold < 0 || threshold > 1) {
            throw new IllegalArgumentException("RAG retrieval similarity threshold must be between 0 and 1");
        }
    }
}
