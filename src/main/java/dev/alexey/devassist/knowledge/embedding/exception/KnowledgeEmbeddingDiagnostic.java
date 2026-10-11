package dev.alexey.devassist.knowledge.embedding.exception;

import java.io.Serial;
import java.io.Serializable;

/** Safe internal fields only. Never include provider messages, headers, bodies or input. */
public record KnowledgeEmbeddingDiagnostic(String provider, Category category,
        Integer httpStatus, String providerErrorCode) implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

    public enum Category {
        TIMEOUT, HTTP_ERROR, TRANSPORT_ERROR, UNKNOWN
    }
}
