package dev.alexey.devassist.knowledge.embedding.exception;

public class KnowledgeEmbeddingTimeoutException extends KnowledgeEmbeddingException {
    public KnowledgeEmbeddingTimeoutException() { super("Embedding provider timed out"); }
}
