package dev.alexey.devassist.analysis.grounding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rag.grounding")
public record GroundingProperties(int maxContextChars, int maxChunks) {
    public GroundingProperties {
        if (maxContextChars < 500 || maxContextChars > 50000 || maxChunks < 1 || maxChunks > 20) {
            throw new IllegalArgumentException("Invalid RAG grounding context limits");
        }
    }
}
