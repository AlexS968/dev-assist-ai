package dev.alexey.devassist.knowledge.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag.retrieval")
public record RetrievalProperties(int topK, int maxTopK, double similarityThreshold) {
    public RetrievalProperties {
        if (maxTopK < 1 || maxTopK > 100 || topK < 1 || topK > maxTopK) {
            throw new IllegalArgumentException("RAG retrieval topK must be between 1 and maxTopK, and maxTopK between 1 and 100");
        }
        if (!Double.isFinite(similarityThreshold) || similarityThreshold < 0 || similarityThreshold > 1) {
            throw new IllegalArgumentException("RAG retrieval similarity threshold must be between 0 and 1");
        }
    }
}
