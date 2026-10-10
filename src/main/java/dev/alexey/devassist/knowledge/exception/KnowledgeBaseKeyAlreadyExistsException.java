package dev.alexey.devassist.knowledge.exception;

public class KnowledgeBaseKeyAlreadyExistsException extends RuntimeException {
    public KnowledgeBaseKeyAlreadyExistsException() {
        super("KnowledgeBase key already exists");
    }
}
