package dev.alexey.devassist.knowledge.embedding.infrastructure;

import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingGateway;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingRequest;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingResult;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingVector;
import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingException;
import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingResponseException;
import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;

abstract class SpringAiKnowledgeEmbeddingGateway implements KnowledgeEmbeddingGateway {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpringAiKnowledgeEmbeddingGateway.class);
    private final EmbeddingModel model;
    private final EmbeddingProfile profile;
    private final LongSupplier nanoTime;

    SpringAiKnowledgeEmbeddingGateway(EmbeddingModel model, EmbeddingProfile profile, LongSupplier nanoTime) {
        this.model = model;
        this.profile = profile;
        this.nanoTime = nanoTime;
    }

    @Override
    public KnowledgeEmbeddingResult embed(KnowledgeEmbeddingRequest request) {
        if (request == null) throw new IllegalArgumentException("Embedding request is required");
        long start = nanoTime.getAsLong();
        EmbeddingResponse response;
        try {
            response = model.call(new EmbeddingRequest(providerTexts(request), null));
        } catch (RuntimeException exception) {
            var diagnostic = EmbeddingFailureDiagnostics.classify(exception, profile.provider());
            LOGGER.warn("Knowledge embedding failed: provider={}, category={}, httpStatus={}, providerErrorCode={}",
                    diagnostic.provider(), diagnostic.category(), diagnostic.httpStatus(),
                    diagnostic.providerErrorCode());
            if (diagnostic.category()
                    == dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingDiagnostic.Category.TIMEOUT) {
                throw new KnowledgeEmbeddingTimeoutException(diagnostic);
            }
            throw new KnowledgeEmbeddingException("Embedding provider unavailable", diagnostic);
        }
        List<KnowledgeEmbeddingVector> vectors = validateVectors(response, request.texts().size());
        EmbeddingResponseMetadata metadata = response.getMetadata();
        String actualModel = metadata != null && metadata.getModel() != null && !metadata.getModel().isBlank()
                ? metadata.getModel() : profile.model();
        Usage usage = metadata == null ? null : metadata.getUsage();
        Integer inputTokens = usage == null || usage instanceof EmptyUsage ? null : usage.getPromptTokens();
        if ("ollama".equals(profile.provider()) && Integer.valueOf(0).equals(inputTokens)) {
            inputTokens = null;
        }
        Duration latency = Duration.ofNanos(Math.max(0, nanoTime.getAsLong() - start));
        return new KnowledgeEmbeddingResult(vectors, profile.provider(), actualModel,
                profile.dimensions(), inputTokens, latency);
    }

    protected List<String> providerTexts(KnowledgeEmbeddingRequest request) {
        return request.texts();
    }

    private List<KnowledgeEmbeddingVector> validateVectors(EmbeddingResponse response, int count) {
        if (response == null || response.getResults() == null || response.getResults().size() != count) {
            throw new KnowledgeEmbeddingResponseException();
        }
        List<KnowledgeEmbeddingVector> vectors = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Embedding item = response.getResults().get(index);
            if (item == null || item.getIndex() == null || item.getIndex() != index) {
                throw new KnowledgeEmbeddingResponseException();
            }
            try {
                vectors.add(new KnowledgeEmbeddingVector(item.getOutput()));
            } catch (IllegalArgumentException exception) {
                throw new KnowledgeEmbeddingResponseException();
            }
        }
        return List.copyOf(vectors);
    }

}
