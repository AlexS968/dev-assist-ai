package dev.alexey.devassist.knowledge.model;

import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import java.util.UUID;

public record IngestKnowledgeDocumentCommand(UUID knowledgeBaseId, String externalId, String title,
        KnowledgeSourceType sourceType, String sourceUri, String content) {}
