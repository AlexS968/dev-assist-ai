package dev.alexey.devassist.knowledge.embedding.infrastructure;

import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import java.util.function.LongSupplier;
import org.springframework.ai.embedding.EmbeddingModel;

public final class OllamaKnowledgeEmbeddingGateway extends SpringAiKnowledgeEmbeddingGateway {
    public OllamaKnowledgeEmbeddingGateway(EmbeddingModel model, EmbeddingProfile profile, LongSupplier nanoTime) {
        super(model, profile, nanoTime);
    }
}
