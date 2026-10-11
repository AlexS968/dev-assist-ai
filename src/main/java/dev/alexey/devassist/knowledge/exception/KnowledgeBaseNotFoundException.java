package dev.alexey.devassist.knowledge.exception;

import java.util.UUID;

public class KnowledgeBaseNotFoundException extends RuntimeException {
    public KnowledgeBaseNotFoundException(UUID id) {
        super("KnowledgeBase not found: " + id);
    }
}
