package dev.alexey.devassist.knowledge.embedding.persistence;

import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import dev.alexey.devassist.knowledge.embedding.KnowledgeChunkEmbeddingCandidate;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingVector;
import dev.alexey.devassist.knowledge.entity.KnowledgeChunk;
import dev.alexey.devassist.knowledge.repository.KnowledgeChunkRepository;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.rag.enabled", havingValue = "true")
public class KnowledgeEmbeddingWriter {
    public enum Outcome { EMBEDDED, MISSING, CONTENT_CHANGED, ALREADY_COMPATIBLE }

    private final KnowledgeChunkRepository chunks;

    public KnowledgeEmbeddingWriter(KnowledgeChunkRepository chunks) {
        this.chunks = chunks;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome write(KnowledgeChunkEmbeddingCandidate candidate, KnowledgeEmbeddingVector vector,
            EmbeddingProfile profile) {
        KnowledgeChunk chunk = chunks.findById(candidate.chunkId()).orElse(null);
        if (chunk == null || !chunk.getDocument().getKnowledgeBase().getId().equals(candidate.knowledgeBaseId())) {
            return Outcome.MISSING;
        }
        if (!chunk.getContentHash().equals(candidate.contentHash())) return Outcome.CONTENT_CHANGED;
        if (profile.matches(chunk.getEmbeddingProvider(), chunk.getEmbeddingModel(), chunk.getEmbeddingDimensions())) {
            return Outcome.ALREADY_COMPATIBLE;
        }
        chunk.setEmbedding(vector.values(), profile, Instant.now());
        chunks.flush();
        return Outcome.EMBEDDED;
    }
}
