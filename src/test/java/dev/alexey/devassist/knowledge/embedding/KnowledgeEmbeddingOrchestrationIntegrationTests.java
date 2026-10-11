package dev.alexey.devassist.knowledge.embedding;

import dev.alexey.devassist.knowledge.embedding.persistence.KnowledgeEmbeddingWriter;
import dev.alexey.devassist.knowledge.entity.KnowledgeBase;
import dev.alexey.devassist.knowledge.entity.KnowledgeChunk;
import dev.alexey.devassist.knowledge.entity.KnowledgeDocument;
import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import dev.alexey.devassist.knowledge.repository.KnowledgeBaseRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeChunkRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeDocumentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(properties = {"app.ai.provider=ollama", "app.rag.enabled=true",
        "app.rag.embedding.provider=ollama", "app.rag.embedding.batch-size=2",
        "app.rag.embedding.max-chunks-per-run=2"})
class KnowledgeEmbeddingOrchestrationIntegrationTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg17-bookworm").asCompatibleSubstituteFor("postgres"));
    @Autowired KnowledgeBaseRepository bases;
    @Autowired KnowledgeDocumentRepository documents;
    @Autowired KnowledgeChunkRepository chunks;
    @Autowired KnowledgeEmbeddingOrchestrator orchestrator;
    @Autowired KnowledgeEmbeddingWriter writer;
    @Autowired EmbeddingProfile profile;
    @MockitoBean KnowledgeEmbeddingGateway gateway;

    private KnowledgeBase base() {
        String key = "kb-" + UUID.randomUUID();
        return bases.saveAndFlush(new KnowledgeBase(key, key, null));
    }

    private KnowledgeChunk chunk(KnowledgeBase base, int index) {
        KnowledgeDocument document = documents.saveAndFlush(new KnowledgeDocument(base, UUID.randomUUID().toString(),
                "Title", KnowledgeSourceType.RUNBOOK, null, "a".repeat(64)));
        return chunks.saveAndFlush(new KnowledgeChunk(document, index, "content " + index,
                "b".repeat(63) + index, Map.of("offsetStart", "0")));
    }

    private KnowledgeEmbeddingVector vector(float value) {
        float[] values = new float[768];
        Arrays.fill(values, value);
        return new KnowledgeEmbeddingVector(values);
    }

    private KnowledgeEmbeddingResult result(KnowledgeEmbeddingRequest request) {
        return new KnowledgeEmbeddingResult(request.texts().stream().map(text -> vector(0.5f)).toList(),
                profile.provider(), profile.model(), 768, 4, Duration.ofMillis(3));
    }

    @Test void runPersistsProfileAndIsIdempotent() {
        KnowledgeBase base = base();
        KnowledgeChunk first = chunk(base, 0);
        when(gateway.embed(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return result(invocation.getArgument(0));
        });
        KnowledgeEmbeddingRunResult result = orchestrator.embedKnowledgeBase(base.getId());
        assertThat(result.embeddedCount()).isEqualTo(1);
        KnowledgeChunk loaded = chunks.findById(first.getId()).orElseThrow();
        assertThat(loaded.getEmbedding()).hasSize(768).startsWith(0.5f);
        assertThat(loaded.getEmbeddingProvider()).isEqualTo(profile.provider());
        assertThat(loaded.getEmbeddingModel()).isEqualTo(profile.model());
        assertThat(loaded.getVersion()).isPositive();
        KnowledgeEmbeddingRunResult repeat = orchestrator.embedKnowledgeBase(base.getId());
        assertThat(repeat.discoveredCount()).isZero();
        verify(gateway).embed(any());
    }

    @Test void candidateQuerySelectsOnlyMissingAndIncompatibleWithinBase() {
        KnowledgeBase base = base();
        KnowledgeChunk missing = chunk(base, 0);
        KnowledgeChunk compatible = chunk(base, 1);
        compatible.setEmbedding(vector(1).values(), profile, Instant.now());
        chunks.saveAndFlush(compatible);
        KnowledgeChunk otherProvider = chunk(base, 2);
        otherProvider.setEmbedding(vector(1).values(), new EmbeddingProfile("openai", profile.model(), 768), Instant.now());
        chunks.saveAndFlush(otherProvider);
        KnowledgeChunk otherModel = chunk(base, 4);
        otherModel.setEmbedding(vector(1).values(), new EmbeddingProfile(profile.provider(), "other", 768), Instant.now());
        chunks.saveAndFlush(otherModel);
        chunk(base(), 3);
        List<KnowledgeChunkEmbeddingCandidate> selected = chunks.findEmbeddingCandidates(base.getId(),
                profile.provider(), profile.model(), 768, PageRequest.of(0, 4));
        assertThat(selected).extracting(KnowledgeChunkEmbeddingCandidate::chunkId)
                .containsExactlyInAnyOrder(missing.getId(), otherProvider.getId(), otherModel.getId());
    }

    @Test void writerSkipsMissingChangedAndAlreadyCompatible() {
        KnowledgeBase base = base();
        KnowledgeChunk persisted = chunk(base, 0);
        KnowledgeChunkEmbeddingCandidate snapshot = new KnowledgeChunkEmbeddingCandidate(persisted.getId(),
                base.getId(), persisted.getContent(), persisted.getContentHash());
        assertThat(writer.write(snapshot, vector(1), profile)).isEqualTo(KnowledgeEmbeddingWriter.Outcome.EMBEDDED);
        assertThat(writer.write(snapshot, vector(2), profile))
                .isEqualTo(KnowledgeEmbeddingWriter.Outcome.ALREADY_COMPATIBLE);
        KnowledgeChunkEmbeddingCandidate stale = new KnowledgeChunkEmbeddingCandidate(persisted.getId(),
                base.getId(), persisted.getContent(), "different");
        assertThat(writer.write(stale, vector(2), profile))
                .isEqualTo(KnowledgeEmbeddingWriter.Outcome.CONTENT_CHANGED);
        chunks.deleteById(persisted.getId());
        assertThat(writer.write(snapshot, vector(2), profile)).isEqualTo(KnowledgeEmbeddingWriter.Outcome.MISSING);
    }

    @Test void incompatibleEmbeddingIsReplacedOnlyAfterSuccessfulProviderCall() {
        KnowledgeBase base = base();
        KnowledgeChunk chunk = chunk(base, 0);
        chunk.setEmbedding(vector(0.1f).values(), new EmbeddingProfile("openai", "old", 768), Instant.now());
        chunks.saveAndFlush(chunk);
        when(gateway.embed(any())).thenThrow(new dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingException(
                "Embedding provider unavailable"));
        assertThatThrownBy(() -> orchestrator.embedKnowledgeBase(base.getId()))
                .isInstanceOf(dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingException.class);
        assertThat(chunks.findById(chunk.getId()).orElseThrow().getEmbedding()[0]).isEqualTo(0.1f);
        reset(gateway);
        when(gateway.embed(any())).thenAnswer(invocation -> result(invocation.getArgument(0)));
        assertThat(orchestrator.embedKnowledgeBase(base.getId()).embeddedCount()).isEqualTo(1);
        KnowledgeChunk reloaded = chunks.findById(chunk.getId()).orElseThrow();
        assertThat(reloaded.getEmbedding()[0]).isEqualTo(0.5f);
        assertThat(reloaded.getEmbeddingProvider()).isEqualTo(profile.provider());
    }

    @Test void emptyAndUnknownBaseDoNotCallProvider() {
        KnowledgeBase empty = base();
        assertThat(orchestrator.embedKnowledgeBase(empty.getId()).embeddedCount()).isZero();
        UUID unknown = UUID.randomUUID();
        assertThatThrownBy(() -> orchestrator.embedKnowledgeBase(unknown))
                .isInstanceOf(dev.alexey.devassist.knowledge.exception.KnowledgeBaseNotFoundException.class);
        verifyNoInteractions(gateway);
    }

    @Test void versionPreventsStaleDetachedWrite() {
        KnowledgeChunk saved = chunk(base(), 0);
        KnowledgeChunk stale = chunks.findById(saved.getId()).orElseThrow();
        KnowledgeChunk fresh = chunks.findById(saved.getId()).orElseThrow();
        fresh.setEmbedding(vector(0.5f).values(), profile, Instant.now());
        chunks.saveAndFlush(fresh);
        stale.setEmbedding(vector(0.8f).values(), profile, Instant.now());
        assertThatThrownBy(() -> chunks.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(chunks.findById(saved.getId()).orElseThrow().getEmbedding()[0]).isEqualTo(0.5f);
    }
}
