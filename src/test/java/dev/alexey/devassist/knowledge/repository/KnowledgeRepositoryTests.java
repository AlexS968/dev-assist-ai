package dev.alexey.devassist.knowledge.repository;

import dev.alexey.devassist.knowledge.entity.*;
import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest
@Transactional
class KnowledgeRepositoryTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(org.testcontainers.utility.DockerImageName.parse("pgvector/pgvector:0.8.6-pg17-bookworm").asCompatibleSubstituteFor("postgres"));
    @Autowired KnowledgeBaseRepository bases;
    @Autowired KnowledgeDocumentRepository documents;
    @Autowired KnowledgeChunkRepository chunks;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    private KnowledgeBase base(String key) {
        return bases.saveAndFlush(new KnowledgeBase(key, key, null));
    }
    private KnowledgeDocument document(KnowledgeBase base, String externalId) {
        return documents.saveAndFlush(new KnowledgeDocument(base, externalId, "Title",
                KnowledgeSourceType.RUNBOOK, null, "a".repeat(64)));
    }
    private KnowledgeChunk chunk(KnowledgeDocument document, int index, Map<String, String> metadata) {
        return chunks.saveAndFlush(new KnowledgeChunk(document, index, "content", "b".repeat(64), metadata));
    }
    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    @Test void vectorExtensionIsInstalled() {
        assertThat(jdbc.queryForObject("SELECT extname FROM pg_extension WHERE extname = 'vector'", String.class))
                .isEqualTo("vector");
    }
    @Test void emptyBasePersistsWithTimestamps() {
        KnowledgeBase base = base("payments");
        entityManager.clear();
        KnowledgeBase loaded = bases.findById(base.getId()).orElseThrow();
        assertThat(loaded.getKey()).isEqualTo("payments");
        assertThat(loaded.getCreatedAt()).isNotNull().isBeforeOrEqualTo(Instant.now());
        assertThat(loaded.getUpdatedAt()).isEqualTo(loaded.getCreatedAt());
        assertThat(count("knowledge_documents")).isZero();
    }
    @Test void duplicateBaseKeyIsRejected() {
        base("payments");
        KnowledgeBase duplicate = new KnowledgeBase("payments", "Other", null);
        assertThatThrownBy(() -> bases.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void documentsBelongToTheirBaseAndExternalIdIsScoped() {
        KnowledgeBase first = base("payments");
        KnowledgeBase second = base("orders");
        KnowledgeDocument one = document(first, "shared");
        KnowledgeDocument two = document(second, "shared");
        entityManager.clear();
        assertThat(documents.findById(one.getId()).orElseThrow().getKnowledgeBase().getId()).isEqualTo(first.getId());
        assertThat(documents.findById(two.getId()).orElseThrow().getKnowledgeBase().getId()).isEqualTo(second.getId());
        assertThat(count("knowledge_documents")).isEqualTo(2);
    }
    @Test void duplicateExternalIdWithinBaseIsRejected() {
        KnowledgeBase base = base("payments");
        document(base, "same");
        KnowledgeDocument duplicate = new KnowledgeDocument(base, "same", "Duplicate",
                KnowledgeSourceType.ARCHITECTURE_NOTE, null, "c".repeat(64));
        assertThatThrownBy(() -> documents.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void chunksPersistWithJsonAndDefensiveCopies() {
        KnowledgeDocument document = document(base("payments"), "runbook");
        Map<String, String> input = new HashMap<>();
        input.put("section", "one");
        KnowledgeChunk first = chunk(document, 0, input);
        input.put("section", "changed");
        KnowledgeChunk second = chunk(document, 1, Map.of());
        assertThat(first.getMetadata()).containsEntry("section", "one");
        Map<String, String> returned = first.getMetadata();
        assertThatThrownBy(() -> returned.put("other", "value")).isInstanceOf(UnsupportedOperationException.class);
        entityManager.clear();
        KnowledgeChunk loaded = chunks.findById(first.getId()).orElseThrow();
        assertThat(loaded.getMetadata()).containsEntry("section", "one");
        assertThat(loaded.getDocument().getId()).isEqualTo(document.getId());
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(chunks.findById(second.getId())).isPresent();
        assertThat(jdbc.queryForObject("SELECT source_type FROM knowledge_documents WHERE id = ?", String.class,
                document.getId())).isEqualTo("RUNBOOK");
    }
    @Test void duplicateChunkIndexIsRejected() {
        KnowledgeDocument document = document(base("payments"), "runbook");
        chunk(document, 0, Map.of());
        KnowledgeChunk duplicate = new KnowledgeChunk(document, 0, "other", "b".repeat(64), Map.of());
        assertThatThrownBy(() -> chunks.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void negativeChunkIndexIsRejected() {
        KnowledgeDocument document = document(base("payments"), "runbook");
        KnowledgeChunk invalid = new KnowledgeChunk(document, -1, "content", "b".repeat(64), Map.of());
        assertThatThrownBy(() -> chunks.saveAndFlush(invalid)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void deletingDocumentCascadesToChunks() {
        KnowledgeDocument document = document(base("payments"), "runbook");
        chunk(document, 0, Map.of());
        entityManager.flush();
        entityManager.clear();
        jdbc.update("DELETE FROM knowledge_documents WHERE id = ?", document.getId());
        assertThat(count("knowledge_chunks")).isZero();
    }
    @Test void deletingBaseCascadesOnlyItsOwnData() {
        KnowledgeBase first = base("payments");
        KnowledgeBase second = base("orders");
        chunk(document(first, "runbook"), 0, Map.of());
        chunk(document(second, "runbook"), 0, Map.of());
        entityManager.flush();
        entityManager.clear();
        jdbc.update("DELETE FROM knowledge_bases WHERE id = ?", first.getId());
        assertThat(count("knowledge_documents")).isEqualTo(1);
        assertThat(count("knowledge_chunks")).isEqualTo(1);
        assertThat(bases.findById(second.getId())).isPresent();
    }
    @Test void documentUpdateChangesUpdatedAtAndPreservesCreatedAt() {
        KnowledgeDocument document = document(base("payments"), "runbook");
        Instant created = document.getCreatedAt();
        document.update("Revised", document.getSourceType(), document.getSourceUri(), document.getContentHash());
        documents.saveAndFlush(document);
        entityManager.clear();
        KnowledgeDocument loaded = documents.findById(document.getId()).orElseThrow();
        assertThat(loaded.getTitle()).isEqualTo("Revised");
        assertThat(loaded.getCreatedAt()).isEqualTo(created);
        assertThat(loaded.getUpdatedAt()).isAfterOrEqualTo(created);
    }
}
