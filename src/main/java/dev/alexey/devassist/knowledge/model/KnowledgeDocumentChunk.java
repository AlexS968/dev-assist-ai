package dev.alexey.devassist.knowledge.model;

import java.util.Map;

public record KnowledgeDocumentChunk(int chunkIndex, String content, Map<String, String> metadata) {
    public KnowledgeDocumentChunk {
        metadata = Map.copyOf(metadata);
    }
}
