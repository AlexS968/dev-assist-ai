package dev.alexey.devassist.knowledge.embedding;

import java.util.UUID;

public record KnowledgeChunkEmbeddingCandidate(UUID chunkId, UUID knowledgeBaseId,
        String content, String contentHash) {}
