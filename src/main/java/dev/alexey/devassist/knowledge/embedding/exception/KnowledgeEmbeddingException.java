package dev.alexey.devassist.knowledge.embedding.exception;

import java.io.Serial;

public class KnowledgeEmbeddingException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;
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
