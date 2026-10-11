package dev.alexey.devassist.knowledge.embedding.infrastructure;

import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import java.util.function.LongSupplier;
import org.springframework.ai.embedding.EmbeddingModel;

public final class OpenAiKnowledgeEmbeddingGateway extends SpringAiKnowledgeEmbeddingGateway {
    public OpenAiKnowledgeEmbeddingGateway(EmbeddingModel model, EmbeddingProfile profile, LongSupplier nanoTime) {
        super(model, profile, nanoTime);
    }
}
