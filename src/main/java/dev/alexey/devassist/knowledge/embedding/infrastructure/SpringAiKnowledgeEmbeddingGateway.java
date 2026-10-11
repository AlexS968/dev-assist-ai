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
import org.jspecify.annotations.Nullable;
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
        @Nullable EmbeddingResponse response;
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
        if (response == null) throw new KnowledgeEmbeddingResponseException();
        List<KnowledgeEmbeddingVector> vectors = validateVectors(response, request.texts().size());
        @Nullable EmbeddingResponseMetadata metadata = response.getMetadata();
        String actualModel = responseModel(metadata);
        Integer inputTokens = inputTokens(metadata);
        Duration latency = Duration.ofNanos(Math.max(0, nanoTime.getAsLong() - start));
        return new KnowledgeEmbeddingResult(vectors, profile.provider(), actualModel,
                profile.dimensions(), inputTokens, latency);
    }

    protected List<String> providerTexts(KnowledgeEmbeddingRequest request) {
        return request.texts();
    }

    private String responseModel(@Nullable EmbeddingResponseMetadata metadata) {
        @Nullable String reported = metadata == null ? null : metadata.getModel();
        return reported == null || reported.isBlank() ? profile.model() : reported;
    }

    private @Nullable Integer inputTokens(@Nullable EmbeddingResponseMetadata metadata) {
        @Nullable Usage usage = metadata == null ? null : metadata.getUsage();
        if (usage == null || usage instanceof EmptyUsage) return null;
        Integer reported = usage.getPromptTokens();
        return "ollama".equals(profile.provider()) && Integer.valueOf(0).equals(reported) ? null : reported;
    }

    private List<KnowledgeEmbeddingVector> validateVectors(EmbeddingResponse response, int count) {
        // Spring AI 2.0.1 constructors accept null results despite non-null getter declarations.
        @Nullable List<Embedding> results = response.getResults();
        if (results == null || results.size() != count) {
            throw new KnowledgeEmbeddingResponseException();
        }
        List<KnowledgeEmbeddingVector> vectors = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            @Nullable Embedding item = results.get(index);
            @Nullable Integer actualIndex = item == null ? null : item.getIndex();
            if (!Integer.valueOf(index).equals(actualIndex)) {
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
