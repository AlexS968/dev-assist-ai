package dev.alexey.devassist.knowledge.retrieval;

import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import dev.alexey.devassist.knowledge.embedding.EmbeddingPurpose;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingGateway;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingRequest;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingResult;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingVector;
import dev.alexey.devassist.knowledge.entity.KnowledgeBase;
import dev.alexey.devassist.knowledge.entity.KnowledgeChunk;
import dev.alexey.devassist.knowledge.entity.KnowledgeDocument;
import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import dev.alexey.devassist.knowledge.repository.KnowledgeBaseRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeChunkRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeDocumentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(properties = {"app.ai.provider=ollama", "app.rag.enabled=true",
        "app.rag.embedding.provider=ollama"})
class KnowledgeRetrievalIntegrationTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg17-bookworm").asCompatibleSubstituteFor("postgres"));
    @Autowired KnowledgeBaseRepository bases;
    @Autowired KnowledgeDocumentRepository documents;
    @Autowired KnowledgeChunkRepository chunks;
    @Autowired KnowledgeRetrievalService retrieval;
    @Autowired EmbeddingProfile profile;
    @Autowired TransactionTemplate transactions;
    @MockitoBean KnowledgeEmbeddingGateway gateway;

    private KnowledgeBase base() {
        String key = "retrieval-" + UUID.randomUUID();
        return bases.saveAndFlush(new KnowledgeBase(key, key, null));
    }

    private float[] vector(float first, float second) {
        float[] values = new float[768];
        values[0] = first;
        values[1] = second;
        return values;
    }

    private KnowledgeChunk chunk(KnowledgeBase base, int index, float[] values, EmbeddingProfile embeddingProfile) {
        KnowledgeDocument document = documents.saveAndFlush(new KnowledgeDocument(base, "external-" + UUID.randomUUID(),
                "Document title", KnowledgeSourceType.RUNBOOK, null, "a".repeat(64)));
        KnowledgeChunk chunk = new KnowledgeChunk(document, index, "untrusted content " + index,
                "b".repeat(64), Map.of("section", "one"));
        if (values != null) chunk.setEmbedding(values, embeddingProfile, Instant.now());
        return chunks.saveAndFlush(chunk);
    }

    private void stubQueryEmbedding() {
        when(gateway.embed(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            KnowledgeEmbeddingRequest request = invocation.getArgument(0);
            assertThat(request.purpose()).isEqualTo(EmbeddingPurpose.QUERY);
            return new KnowledgeEmbeddingResult(java.util.List.of(new KnowledgeEmbeddingVector(vector(1, 0))),
                    profile.provider(), profile.model(), 768, 3, Duration.ofMillis(2));
        });
    }

    @Test void exactSearchFiltersOrdersAndProjectsMetadata() {
        KnowledgeBase target = base();
        KnowledgeChunk best = chunk(target, 0, vector(1, 0), profile);
        KnowledgeChunk second = chunk(target, 1, vector(1, 1), profile);
        chunk(target, 2, vector(0, 1), profile);
        chunk(target, 3, null, profile);
        chunk(target, 4, vector(1, 0), new EmbeddingProfile("openai", "other", 768));
        chunk(target, 5, vector(1, 0), new EmbeddingProfile(profile.provider(), "other", 768));
        chunk(base(), 6, vector(1, 0), profile);
        stubQueryEmbedding();
        KnowledgeRetrievalResult result = retrieval.retrieve(new KnowledgeRetrievalRequest(target.getId(),
                "question", 2, 0.7));
        assertThat(result.matches()).extracting(RetrievedKnowledgeChunk::chunkId)
                .containsExactly(best.getId(), second.getId());
        assertThat(result.matches().get(0).similarity()).isCloseTo(1.0, within(0.000001));
        assertThat(result.matches().get(1).similarity()).isCloseTo(Math.sqrt(0.5), within(0.000001));
        RetrievedKnowledgeChunk found = result.matches().getFirst();
        assertThat(found.documentId()).isEqualTo(best.getDocument().getId());
        assertThat(found.externalId()).startsWith("external-");
        assertThat(found.documentTitle()).isEqualTo("Document title");
        assertThat(found.sourceType()).isEqualTo(KnowledgeSourceType.RUNBOOK);
        assertThat(found.sourceUri()).isNull();
        assertThat(found.metadata()).containsEntry("section", "one");
        Map<String, String> metadata = found.metadata();
        assertThatThrownBy(() -> metadata.put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
        var immutableMatches = result.matches();
        assertThatThrownBy(immutableMatches::clear)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(result.inputTokens()).isEqualTo(3);
        assertThat(result.embeddingLatencyMs()).isEqualTo(2);
        verify(gateway).embed(any());
    }

    @Test void emptyAndStaleCorpusAvoidsProvider() {
        KnowledgeBase target = base();
        assertThat(retrieval.retrieve(new KnowledgeRetrievalRequest(target.getId(), "query", null, null)).matches())
                .isEmpty();
        documents.saveAndFlush(new KnowledgeDocument(target, "empty-" + UUID.randomUUID(),
                "No chunks", KnowledgeSourceType.RUNBOOK, null, "a".repeat(64)));
        assertThat(retrieval.retrieve(new KnowledgeRetrievalRequest(target.getId(), "query", null, null)).matches())
                .isEmpty();
        chunk(target, 0, null, profile);
        chunk(target, 1, vector(1, 0), new EmbeddingProfile("openai", "old", 768));
        assertThat(retrieval.retrieve(new KnowledgeRetrievalRequest(target.getId(), "query", null, null)).matches())
                .isEmpty();
        verifyNoInteractions(gateway);
    }

    @Test void thresholdAndTieOrderAreAppliedInDatabase() {
        KnowledgeBase target = base();
        KnowledgeChunk first = chunk(target, 0, vector(1, 0), profile);
        KnowledgeChunk second = chunk(target, 1, vector(1, 0), profile);
        chunk(target, 2, vector(0, 1), profile);
        stubQueryEmbedding();
        KnowledgeRetrievalResult result = retrieval.retrieve(new KnowledgeRetrievalRequest(target.getId(),
                "question", 10, 0.99));
        assertThat(result.matches()).hasSize(2);
        assertThat(result.matches()).extracting(RetrievedKnowledgeChunk::chunkId)
                .containsExactlyElementsOf(java.util.stream.Stream.of(first.getId(), second.getId())
                        .sorted(java.util.Comparator.comparing(UUID::toString)).toList());
        KnowledgeRetrievalResult none = retrieval.retrieve(new KnowledgeRetrievalRequest(target.getId(),
                "question", null, 1.0));
        assertThat(none.matches()).hasSize(2);
        KnowledgeBase unrelated = base();
        chunk(unrelated, 0, vector(0, 1), profile);
        assertThat(retrieval.retrieve(new KnowledgeRetrievalRequest(unrelated.getId(),
                "question", null, 0.7)).matches()).isEmpty();
    }

    @Test void providerCallSuspendsCallingTransaction() {
        KnowledgeBase target = base();
        chunk(target, 0, vector(1, 0), profile);
        stubQueryEmbedding();
        KnowledgeRetrievalResult result = transactions.execute(status -> retrieval.retrieve(
                new KnowledgeRetrievalRequest(target.getId(), "question", null, null)));
        assertThat(result).isNotNull();
        assertThat(result.matches()).hasSize(1);
    }
}
