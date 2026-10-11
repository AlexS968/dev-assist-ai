package dev.alexey.devassist.knowledge.embedding.infrastructure;

import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import dev.alexey.devassist.knowledge.embedding.EmbeddingPurpose;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingRequest;
import java.util.List;
import java.util.function.LongSupplier;
import org.springframework.ai.embedding.EmbeddingModel;

public final class OllamaKnowledgeEmbeddingGateway extends SpringAiKnowledgeEmbeddingGateway {
    public OllamaKnowledgeEmbeddingGateway(EmbeddingModel model, EmbeddingProfile profile, LongSupplier nanoTime) {
        super(model, profile, nanoTime);
    }

    @Override
    protected List<String> providerTexts(KnowledgeEmbeddingRequest request) {
        String prefix = request.purpose() == EmbeddingPurpose.DOCUMENT ? "search_document: " : "search_query: ";
        return request.texts().stream().map(text -> prefix + text).toList();
    }
}
