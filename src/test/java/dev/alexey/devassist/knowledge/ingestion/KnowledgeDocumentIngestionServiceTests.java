package dev.alexey.devassist.knowledge.ingestion;

import dev.alexey.devassist.knowledge.entity.KnowledgeBase;
import dev.alexey.devassist.knowledge.entity.KnowledgeChunk;
import dev.alexey.devassist.knowledge.entity.KnowledgeDocument;
import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import dev.alexey.devassist.knowledge.exception.KnowledgeBaseNotFoundException;
import dev.alexey.devassist.knowledge.model.IngestKnowledgeDocumentCommand;
import dev.alexey.devassist.knowledge.model.KnowledgeDocumentIngestionResult;
import dev.alexey.devassist.knowledge.model.KnowledgeDocumentIngestionStatus;
import dev.alexey.devassist.knowledge.repository.KnowledgeBaseRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeChunkRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeDocumentRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest
@Transactional
class KnowledgeDocumentIngestionServiceTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg17-bookworm").asCompatibleSubstituteFor("postgres"));
    @Autowired KnowledgeDocumentIngestionService ingestion;
    @Autowired KnowledgeBaseRepository bases;
    @Autowired KnowledgeDocumentRepository documents;
    @Autowired KnowledgeChunkRepository chunks;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired Sha256ContentHasher hasher;
    @Autowired KnowledgeDocumentNormalizer normalizer;

    private KnowledgeBase base(String key) {
        return bases.saveAndFlush(new KnowledgeBase(key, key, null));
    }
    private IngestKnowledgeDocumentCommand command(UUID baseId, String content) {
        return new IngestKnowledgeDocumentCommand(baseId, "runbook", "Runbook",
                KnowledgeSourceType.RUNBOOK, null, content);
    }
    private List<KnowledgeChunk> chunks(UUID documentId) {
        return chunks.findByDocument_IdOrderByChunkIndexAsc(documentId);
    }
    private List<ChunkSnapshot> snapshots(UUID documentId) {
        return chunks(documentId).stream().map(chunk -> new ChunkSnapshot(chunk.getId(), chunk.getContent(),
                chunk.getContentHash(), chunk.getChunkIndex(), chunk.getMetadata())).toList();
    }
    private record ChunkSnapshot(UUID id, String content, String hash, int index, Map<String, String> metadata) {}

    @Test void createsAndReturnsPersistedChunksAndHashes() {
        KnowledgeBase base = base("payments");
        String content = "# Issue\r\n\r\n" + "Check logs. ".repeat(200);
        KnowledgeDocumentIngestionResult result = ingestion.ingest(command(base.getId(), content));
        entityManager.flush();
        entityManager.clear();
        List<KnowledgeChunk> stored = chunks(result.documentId());
        assertThat(result.status()).isEqualTo(KnowledgeDocumentIngestionStatus.CREATED);
        assertThat(result.chunkCount()).isEqualTo(stored.size()).isGreaterThan(1);
        assertThat(result.contentHash()).isEqualTo(hasher.hash(normalizer.normalize(content)));
        for (int index = 0; index < stored.size(); index++) {
            KnowledgeChunk chunk = stored.get(index);
            assertThat(chunk.getChunkIndex()).isEqualTo(index);
            assertThat(chunk.getContentHash()).isEqualTo(hasher.hash(chunk.getContent()));
            assertThat(chunk.getMetadata()).containsEntry("sourceType", "RUNBOOK");
        }
    }

    @Test void identicalIngestionPreservesDocumentChunksAndTimestamp() {
        KnowledgeBase base = base("payments");
        IngestKnowledgeDocumentCommand command = command(base.getId(), "# Runbook\nFollow steps.");
        KnowledgeDocumentIngestionResult created = ingestion.ingest(command);
        entityManager.flush();
        entityManager.clear();
        Instant before = documents.findById(created.documentId()).orElseThrow().getUpdatedAt();
        List<ChunkSnapshot> beforeChunks = snapshots(created.documentId());
        KnowledgeDocumentIngestionResult again = ingestion.ingest(command);
        entityManager.flush();
        entityManager.clear();
        assertThat(again.status()).isEqualTo(KnowledgeDocumentIngestionStatus.UNCHANGED);
        assertThat(again.documentId()).isEqualTo(created.documentId());
        assertThat(again.chunkCount()).isEqualTo(beforeChunks.size());
        assertThat(documents.findById(created.documentId()).orElseThrow().getUpdatedAt()).isEqualTo(before);
        assertThat(snapshots(created.documentId())).isEqualTo(beforeChunks);
    }

    @Test void sourceTypeUpdateSynchronizesExistingChunksWithoutChangingOtherFields() {
        KnowledgeBase base = base("payments");
        String content = "# Runbook\n" + "Check steps. ".repeat(200);
        KnowledgeDocumentIngestionResult created = ingestion.ingest(command(base.getId(), content));
        entityManager.flush();
        entityManager.clear();
        List<ChunkSnapshot> before = snapshots(created.documentId());
        assertThat(before).hasSizeGreaterThan(1);
        Instant oldTime = Instant.parse("2020-01-01T00:00:00Z");
        jdbc.update("UPDATE knowledge_documents SET updated_at = ? WHERE id = ?",
                java.sql.Timestamp.from(oldTime), created.documentId());
        entityManager.clear();
        IngestKnowledgeDocumentCommand changed = new IngestKnowledgeDocumentCommand(base.getId(), "runbook", "New title",
                KnowledgeSourceType.TROUBLESHOOTING_GUIDE, "https://example.invalid/doc", content);
        KnowledgeDocumentIngestionResult result = ingestion.ingest(changed);
        entityManager.flush();
        entityManager.clear();
        KnowledgeDocument loaded = documents.findById(created.documentId()).orElseThrow();
        List<ChunkSnapshot> after = snapshots(created.documentId());
        assertThat(result.status()).isEqualTo(KnowledgeDocumentIngestionStatus.UPDATED_METADATA);
        assertThat(loaded.getTitle()).isEqualTo("New title");
        assertThat(loaded.getSourceType()).isEqualTo(KnowledgeSourceType.TROUBLESHOOTING_GUIDE);
        assertThat(loaded.getUpdatedAt()).isAfter(oldTime);
        assertThat(loaded.getContentHash()).isEqualTo(created.contentHash());
        assertThat(after).hasSameSizeAs(before);
        for (int index = 0; index < before.size(); index++) {
            ChunkSnapshot old = before.get(index);
            ChunkSnapshot current = after.get(index);
            assertThat(current.id()).isEqualTo(old.id());
            assertThat(current.content()).isEqualTo(old.content());
            assertThat(current.hash()).isEqualTo(old.hash());
            assertThat(current.index()).isEqualTo(old.index());
            assertThat(current.metadata()).containsEntry("sourceType", "TROUBLESHOOTING_GUIDE")
                    .containsEntry("characterStart", old.metadata().get("characterStart"))
                    .containsEntry("characterEnd", old.metadata().get("characterEnd"));
            Map<String, String> expected = new java.util.HashMap<>(old.metadata());
            expected.put("sourceType", "TROUBLESHOOTING_GUIDE");
            assertThat(current.metadata()).isEqualTo(expected);
        }
        Map<String, String> exposed = chunks(created.documentId()).getFirst().getMetadata();
        assertThatThrownBy(() -> exposed.put("sourceType", "RUNBOOK"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void titleOnlyUpdateDoesNotTouchChunks() {
        KnowledgeBase base = base("payments");
        KnowledgeDocumentIngestionResult created = ingestion.ingest(command(base.getId(), "Steps"));
        List<ChunkSnapshot> before = snapshots(created.documentId());
        IngestKnowledgeDocumentCommand changed = new IngestKnowledgeDocumentCommand(base.getId(), "runbook", "Revised",
                KnowledgeSourceType.RUNBOOK, null, "Steps");
        KnowledgeDocumentIngestionResult result = ingestion.ingest(changed);
        entityManager.flush();
        entityManager.clear();
        assertThat(result.status()).isEqualTo(KnowledgeDocumentIngestionStatus.UPDATED_METADATA);
        assertThat(snapshots(created.documentId())).isEqualTo(before);
    }

    @Test void sourceUriOnlyUpdateDoesNotTouchChunks() {
        KnowledgeBase base = base("payments");
        KnowledgeDocumentIngestionResult created = ingestion.ingest(command(base.getId(), "Steps"));
        List<ChunkSnapshot> before = snapshots(created.documentId());
        IngestKnowledgeDocumentCommand changed = new IngestKnowledgeDocumentCommand(base.getId(), "runbook", "Runbook",
                KnowledgeSourceType.RUNBOOK, "https://example.invalid/runbook", "Steps");
        KnowledgeDocumentIngestionResult result = ingestion.ingest(changed);
        entityManager.flush();
        entityManager.clear();
        assertThat(result.status()).isEqualTo(KnowledgeDocumentIngestionStatus.UPDATED_METADATA);
        assertThat(snapshots(created.documentId())).isEqualTo(before);
    }

    @Test void changedContentReplacesOldChunks() {
        KnowledgeBase base = base("payments");
        KnowledgeDocumentIngestionResult created = ingestion.ingest(command(base.getId(), "Old content"));
        UUID oldChunk = chunks(created.documentId()).getFirst().getId();
        KnowledgeDocumentIngestionResult result = ingestion.ingest(command(base.getId(), "New content"));
        entityManager.flush();
        entityManager.clear();
        assertThat(result.status()).isEqualTo(KnowledgeDocumentIngestionStatus.UPDATED);
        assertThat(result.documentId()).isEqualTo(created.documentId());
        assertThat(chunks.findById(oldChunk)).isEmpty();
        assertThat(chunks(created.documentId())).singleElement().extracting(KnowledgeChunk::getContent).isEqualTo("New content");
    }

    @Test void sameExternalIdInDifferentBasesIsIndependent() {
        KnowledgeBase first = base("payments");
        KnowledgeBase second = base("orders");
        KnowledgeDocumentIngestionResult one = ingestion.ingest(command(first.getId(), "Payment"));
        KnowledgeDocumentIngestionResult two = ingestion.ingest(command(second.getId(), "Order"));
        assertThat(one.documentId()).isNotEqualTo(two.documentId());
        assertThat(one.contentHash()).isNotEqualTo(two.contentHash());
    }

    @Test void unknownBaseAndInvalidCommandAreRejected() {
        IngestKnowledgeDocumentCommand unknown = command(UUID.randomUUID(), "Content");
        assertThatThrownBy(() -> ingestion.ingest(unknown)).isInstanceOf(KnowledgeBaseNotFoundException.class);
        IngestKnowledgeDocumentCommand invalid = command(null, "Content");
        assertThatThrownBy(() -> ingestion.ingest(invalid)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedUpdateRollsBackDocumentAndChunkReplacement() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        UUID baseId = transaction.execute(status -> base("rollback").getId());
        KnowledgeDocumentIngestionResult original = transaction.execute(status ->
                ingestion.ingest(command(baseId, "Original")));
        assertThatThrownBy(() -> updateThenFail(transaction, baseId))
                .isInstanceOf(IllegalStateException.class);
        transaction.executeWithoutResult(status -> {
            KnowledgeDocument loaded = documents.findById(original.documentId()).orElseThrow();
            assertThat(loaded.getContentHash()).isEqualTo(original.contentHash());
            assertThat(chunks(original.documentId())).singleElement()
                    .extracting(KnowledgeChunk::getContent).isEqualTo("Original");
        });
    }

    private void updateThenFail(TransactionTemplate transaction, UUID baseId) {
        transaction.executeWithoutResult(status -> {
            ingestion.ingest(command(baseId, "Replacement"));
            throw new IllegalStateException("simulated downstream failure");
        });
    }

    @Test void emptyKnowledgeBaseRemainsValid() {
        KnowledgeBase base = base("empty");
        assertThat(documents.findByKnowledgeBase_IdAndExternalId(base.getId(), "missing")).isEmpty();
    }
}
