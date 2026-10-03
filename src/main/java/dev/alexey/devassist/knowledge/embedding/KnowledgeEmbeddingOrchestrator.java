package dev.alexey.devassist.knowledge.embedding;

import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingResponseException;
import dev.alexey.devassist.knowledge.embedding.infrastructure.EmbeddingProperties;
import dev.alexey.devassist.knowledge.embedding.persistence.KnowledgeEmbeddingWriter;
import dev.alexey.devassist.knowledge.exception.KnowledgeBaseNotFoundException;
import dev.alexey.devassist.knowledge.repository.KnowledgeBaseRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeChunkRepository;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
@ConditionalOnProperty(name = "app.rag.enabled", havingValue = "true")
public class KnowledgeEmbeddingOrchestrator {
    private final KnowledgeBaseRepository bases;
    private final KnowledgeChunkRepository chunks;
    private final KnowledgeEmbeddingGateway gateway;
    private final KnowledgeEmbeddingWriter writer;
    private final EmbeddingProfile profile;
    private final EmbeddingProperties properties;
    private final LongSupplier nanoTime;

    @Autowired
    public KnowledgeEmbeddingOrchestrator(KnowledgeBaseRepository bases, KnowledgeChunkRepository chunks,
            KnowledgeEmbeddingGateway gateway, KnowledgeEmbeddingWriter writer,
            EmbeddingProfile profile, EmbeddingProperties properties) {
        this(bases, chunks, gateway, writer, profile, properties, System::nanoTime);
    }

    KnowledgeEmbeddingOrchestrator(KnowledgeBaseRepository bases, KnowledgeChunkRepository chunks,
            KnowledgeEmbeddingGateway gateway, KnowledgeEmbeddingWriter writer,
            EmbeddingProfile profile, EmbeddingProperties properties, LongSupplier nanoTime) {
        this.bases = bases;
        this.chunks = chunks;
        this.gateway = gateway;
        this.writer = writer;
        this.profile = profile;
        this.properties = properties;
        this.nanoTime = nanoTime;
    }

    public KnowledgeEmbeddingRunResult embedKnowledgeBase(UUID knowledgeBaseId) {
        long start = nanoTime.getAsLong();
        if (!bases.existsById(knowledgeBaseId)) throw new KnowledgeBaseNotFoundException(knowledgeBaseId);
        List<KnowledgeChunkEmbeddingCandidate> selected = chunks.findEmbeddingCandidates(knowledgeBaseId,
                profile.provider(), profile.model(), profile.dimensions(),
                PageRequest.of(0, properties.maxChunksPerRun() + 1));
        boolean limitReached = selected.size() > properties.maxChunksPerRun();
        List<KnowledgeChunkEmbeddingCandidate> candidates = selected.subList(0,
                Math.min(selected.size(), properties.maxChunksPerRun()));
        Counts counts = new Counts();
        for (int offset = 0; offset < candidates.size(); offset += properties.batchSize()) {
            int end = Math.min(offset + properties.batchSize(), candidates.size());
            embedBatch(candidates.subList(offset, end), counts);
        }
        return new KnowledgeEmbeddingRunResult(knowledgeBaseId, profile, candidates.size(), counts.attempted,
                counts.embedded, counts.missing, counts.contentChanged, counts.alreadyCompatible,
                counts.concurrencyConflict, counts.batches, counts.batches > 0 && counts.tokensKnown ? counts.tokens : null,
                counts.providerLatencyMs, Math.max(0, nanoTime.getAsLong() - start) / 1_000_000, limitReached);
    }

    private void embedBatch(List<KnowledgeChunkEmbeddingCandidate> batch, Counts counts) {
        List<String> texts = batch.stream().map(KnowledgeChunkEmbeddingCandidate::content).toList();
        KnowledgeEmbeddingResult response = gateway.embed(new KnowledgeEmbeddingRequest(texts, EmbeddingPurpose.DOCUMENT));
        validateResponse(response, batch.size());
        counts.batches++;
        counts.attempted += batch.size();
        counts.providerLatencyMs = Math.addExact(counts.providerLatencyMs, response.latency().toMillis());
        if (response.inputTokens() == null) counts.tokensKnown = false;
        else if (counts.tokensKnown) counts.tokens = Math.addExact(counts.tokens, response.inputTokens());
        for (int index = 0; index < batch.size(); index++) {
            try {
                KnowledgeEmbeddingWriter.Outcome outcome = writer.write(batch.get(index), response.vectors().get(index), profile);
                count(outcome, counts);
            } catch (OptimisticLockingFailureException exception) {
                counts.concurrencyConflict++;
            }
        }
    }

    private void validateResponse(KnowledgeEmbeddingResult response, int expectedCount) {
        if (response == null || response.vectors().size() != expectedCount
                || !profile.matches(response.provider(), response.model(), response.dimensions())) {
            throw new KnowledgeEmbeddingResponseException();
        }
    }

    private void count(KnowledgeEmbeddingWriter.Outcome outcome, Counts counts) {
        switch (outcome) {
            case EMBEDDED -> counts.embedded++;
            case MISSING -> counts.missing++;
            case CONTENT_CHANGED -> counts.contentChanged++;
            case ALREADY_COMPATIBLE -> counts.alreadyCompatible++;
        }
    }

    private static final class Counts {
        private int attempted;
        private int embedded;
        private int missing;
        private int contentChanged;
        private int alreadyCompatible;
        private int concurrencyConflict;
        private int batches;
        private long tokens;
        private boolean tokensKnown = true;
        private long providerLatencyMs;
    }
}
