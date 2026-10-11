package dev.alexey.devassist.knowledge.retrieval;

import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import dev.alexey.devassist.knowledge.embedding.EmbeddingPurpose;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingGateway;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingRequest;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingResult;
import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingResponseException;
import dev.alexey.devassist.knowledge.exception.KnowledgeBaseNotFoundException;
import dev.alexey.devassist.knowledge.repository.KnowledgeBaseRepository;
import dev.alexey.devassist.knowledge.retrieval.persistence.KnowledgeVectorSearchRepository;
import java.util.List;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.rag.enabled", havingValue = "true")
public class KnowledgeRetrievalService {
    private final KnowledgeBaseRepository bases;
    private final KnowledgeVectorSearchRepository search;
    private final KnowledgeEmbeddingGateway gateway;
    private final EmbeddingProfile profile;
    private final RetrievalProperties properties;
    private final LongSupplier nanoTime;

    @Autowired
    public KnowledgeRetrievalService(KnowledgeBaseRepository bases, KnowledgeVectorSearchRepository search,
            KnowledgeEmbeddingGateway gateway, EmbeddingProfile profile, RetrievalProperties properties) {
        this(bases, search, gateway, profile, properties, System::nanoTime);
    }

    KnowledgeRetrievalService(KnowledgeBaseRepository bases, KnowledgeVectorSearchRepository search,
            KnowledgeEmbeddingGateway gateway, EmbeddingProfile profile, RetrievalProperties properties,
            LongSupplier nanoTime) {
        this.bases = bases;
        this.search = search;
        this.gateway = gateway;
        this.profile = profile;
        this.properties = properties;
        this.nanoTime = nanoTime;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public KnowledgeRetrievalResult retrieve(KnowledgeRetrievalRequest request) {
        long start = nanoTime.getAsLong();
        if (request == null) throw new IllegalArgumentException("Retrieval request is required");
        int topK = request.topK() == null ? properties.topK() : request.topK();
        double threshold = request.similarityThreshold() == null
                ? properties.thresholdFor(profile) : request.similarityThreshold();
        if (topK > properties.maxTopK()) throw new IllegalArgumentException("Retrieval topK exceeds configured maximum");
        if (!bases.existsById(request.knowledgeBaseId())) {
            throw new KnowledgeBaseNotFoundException(request.knowledgeBaseId());
        }
        if (!search.hasCompatibleEmbeddings(request.knowledgeBaseId(), profile)) {
            return result(request, List.of(), null, 0, 0, start);
        }
        KnowledgeEmbeddingResult embedding = gateway.embed(new KnowledgeEmbeddingRequest(
                List.of(request.query()), EmbeddingPurpose.QUERY));
        validate(embedding);
        long searchStart = nanoTime.getAsLong();
        List<RetrievedKnowledgeChunk> matches = search.search(request.knowledgeBaseId(), profile,
                embedding.vectors().getFirst().values(), topK, threshold);
        long searchLatency = millisSince(searchStart);
        return result(request, matches, embedding.inputTokens(), embedding.latency().toMillis(),
                searchLatency, start);
    }

    private void validate(KnowledgeEmbeddingResult embedding) {
        if (embedding == null || embedding.vectors().size() != 1
                || !profile.matches(embedding.provider(), embedding.model(), embedding.dimensions())) {
            throw new KnowledgeEmbeddingResponseException();
        }
        try {
            new dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingVector(
                    embedding.vectors().getFirst().values());
        } catch (IllegalArgumentException exception) {
            throw new KnowledgeEmbeddingResponseException();
        }
    }

    private KnowledgeRetrievalResult result(KnowledgeRetrievalRequest request, List<RetrievedKnowledgeChunk> matches,
            Integer tokens, long embeddingLatency, long searchLatency, long start) {
        return new KnowledgeRetrievalResult(request.knowledgeBaseId(), profile, matches, tokens,
                embeddingLatency, searchLatency, millisSince(start));
    }

    private long millisSince(long start) {
        return Math.max(0, nanoTime.getAsLong() - start) / 1_000_000;
    }
}
