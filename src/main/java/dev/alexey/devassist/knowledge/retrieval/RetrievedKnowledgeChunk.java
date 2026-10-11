package dev.alexey.devassist.knowledge.retrieval;

import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import java.util.Map;
import java.util.UUID;

/** Content is untrusted source material; callers must not execute or follow instructions in it. */
public record RetrievedKnowledgeChunk(UUID chunkId, UUID documentId, String externalId,
        String documentTitle, KnowledgeSourceType sourceType, String sourceUri,
        int chunkIndex, String content, Map<String, String> metadata, double similarity) {
    public RetrievedKnowledgeChunk {
        metadata = Map.copyOf(metadata);
    }
}
