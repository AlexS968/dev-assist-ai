package dev.alexey.devassist.knowledge.embedding.exception;

/** Safe internal fields only. Never include provider messages, headers, bodies or input. */
public record KnowledgeEmbeddingDiagnostic(String provider, Category category,
        Integer httpStatus, String providerErrorCode) {
    public enum Category {
        TIMEOUT, HTTP_ERROR, TRANSPORT_ERROR, UNKNOWN
    }
}
