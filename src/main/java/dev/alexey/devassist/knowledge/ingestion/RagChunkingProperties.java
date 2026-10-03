package dev.alexey.devassist.knowledge.ingestion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.rag.chunking")
public record RagChunkingProperties(@Min(200) @Max(10000) int maxChars, @Min(0) int overlapChars) {
    public RagChunkingProperties {
        if (maxChars < 200 || maxChars > 10000 || overlapChars < 0 || overlapChars >= maxChars) {
            throw new IllegalArgumentException("Invalid RAG chunking configuration");
        }
    }
}
