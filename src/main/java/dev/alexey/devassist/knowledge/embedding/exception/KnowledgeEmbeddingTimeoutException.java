package dev.alexey.devassist.knowledge.embedding.exception;

public class KnowledgeEmbeddingTimeoutException extends KnowledgeEmbeddingException {
    public KnowledgeEmbeddingTimeoutException() { super("Embedding provider timed out"); }

    public KnowledgeEmbeddingTimeoutException(KnowledgeEmbeddingDiagnostic diagnostic) {
        super("Embedding provider timed out", diagnostic);
    }
}
