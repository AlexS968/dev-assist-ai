package dev.alexey.devassist.knowledge.embedding.exception;

public class KnowledgeEmbeddingResponseException extends KnowledgeEmbeddingException {
    public KnowledgeEmbeddingResponseException() { super("Embedding provider returned an invalid response"); }
}
