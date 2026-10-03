package dev.alexey.devassist.knowledge.embedding;

public interface KnowledgeEmbeddingGateway {
    KnowledgeEmbeddingResult embed(KnowledgeEmbeddingRequest request);
}
