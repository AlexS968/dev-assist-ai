package dev.alexey.devassist.knowledge.embedding.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.rag.embedding")
public record EmbeddingProperties(String provider, String openaiModel, String ollamaModel,
        int dimensions, Duration timeout, int batchSize, int maxChunksPerRun) {
    public EmbeddingProperties {
        if (!"openai".equals(provider) && !"ollama".equals(provider)) {
            throw new IllegalArgumentException("RAG embedding provider must be openai or ollama");
        }
        if (openaiModel == null || openaiModel.isBlank() || ollamaModel == null || ollamaModel.isBlank()) {
            throw new IllegalArgumentException("RAG embedding model is required");
        }
        if (dimensions != 768) throw new IllegalArgumentException("RAG embedding dimensions must be 768");
        if (timeout == null || timeout.compareTo(Duration.ofMillis(1)) < 0
                || timeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("RAG embedding timeout must be between 1ms and 2147483647ms");
        }
        if (batchSize < 1 || batchSize > 256) throw new IllegalArgumentException("RAG embedding batch size must be between 1 and 256");
        if (maxChunksPerRun < 1 || maxChunksPerRun > 10000) throw new IllegalArgumentException("RAG max chunks per run must be between 1 and 10000");
    }
}
