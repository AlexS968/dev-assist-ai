package dev.alexey.devassist.knowledge.model;

import java.util.UUID;

public record KnowledgeDocumentIngestionResult(UUID documentId, UUID knowledgeBaseId,
        String externalId, KnowledgeDocumentIngestionStatus status, long chunkCount, String contentHash) {}
