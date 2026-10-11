package dev.alexey.devassist.knowledge.embedding.exception;

public class KnowledgeEmbeddingException extends RuntimeException {
    private final KnowledgeEmbeddingDiagnostic diagnostic;

    public KnowledgeEmbeddingException(String message) {
        super(message);
        this.diagnostic = null;
    }

    public KnowledgeEmbeddingException(String message, KnowledgeEmbeddingDiagnostic diagnostic) {
        super(message);
        this.diagnostic = diagnostic;
    }

    public KnowledgeEmbeddingDiagnostic diagnostic() { return diagnostic; }
}
