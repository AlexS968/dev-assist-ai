package dev.alexey.devassist.ragevaluation;

import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingGateway;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingRequest;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingResult;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingVector;
import dev.alexey.devassist.knowledge.embedding.EmbeddingPurpose;
import dev.alexey.devassist.knowledge.entity.KnowledgeBase;
import dev.alexey.devassist.knowledge.ingestion.KnowledgeDocumentIngestionService;
import dev.alexey.devassist.knowledge.repository.KnowledgeBaseRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeChunkRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeDocumentRepository;
import dev.alexey.devassist.knowledge.retrieval.KnowledgeRetrievalRequest;
import dev.alexey.devassist.knowledge.retrieval.KnowledgeRetrievalService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
class RagEvaluationRetrievalIntegrationTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg17-bookworm")
                    .asCompatibleSubstituteFor("postgres"));
    @Autowired KnowledgeBaseRepository bases;
    @Autowired KnowledgeDocumentRepository documents;
    @Autowired KnowledgeChunkRepository chunks;
    @Autowired KnowledgeDocumentIngestionService ingestion;
    @Autowired KnowledgeRetrievalService retrieval;
    @Autowired EmbeddingProfile profile;
    @MockitoBean KnowledgeEmbeddingGateway gateway;

    @Test void deterministicVectorsRankPaymentRunbookAndExcludeOrderBase() {
        var payment = bases.saveAndFlush(new KnowledgeBase("eval-payment-" + UUID.randomUUID(), "Payment", null));
        var order = bases.saveAndFlush(new KnowledgeBase("eval-order-" + UUID.randomUUID(), "Order", null));
        RagEvaluationCorpus.ingest(ingestion, RagEvaluationCorpus.PAYMENT, payment.getId());
        RagEvaluationCorpus.ingest(ingestion, RagEvaluationCorpus.ORDER, order.getId());
        assignVectors(payment, false);
        assignVectors(order, true);
        when(gateway.embed(any())).thenAnswer(invocation -> {
            KnowledgeEmbeddingRequest request = invocation.getArgument(0);
            assertThat(request.purpose()).isEqualTo(EmbeddingPurpose.QUERY);
            return new KnowledgeEmbeddingResult(List.of(new KnowledgeEmbeddingVector(axis(0))),
                    profile.provider(), profile.model(), 768, 4, Duration.ofMillis(1));
        });
        var scenario = RagEvaluationCorpus.SCENARIOS.getFirst();
        var result = retrieval.retrieve(new KnowledgeRetrievalRequest(payment.getId(), scenario.query(), 20, 0.0));
        assertThat(result.matches()).isNotEmpty();
        assertThat(result.matches().getFirst().externalId()).isEqualTo("payment-pool-runbook");
        assertThat(result.matches()).extracting(chunk -> chunk.externalId())
                .doesNotContain("order-database-runbook", "order-pool-capacity");
        assertThat(result.matches().getFirst().similarity()).isCloseTo(1.0, within(0.0001));
        verify(gateway, times(1)).embed(any());
    }

    private void assignVectors(KnowledgeBase base, boolean order) {
        for (var document : documents.findAll()) {
            if (!document.getKnowledgeBase().getId().equals(base.getId())) continue;
            int axis = order || document.getExternalId().equals("payment-pool-runbook") ? 0 : 1;
            for (var chunk : chunks.findByDocument_IdOrderByChunkIndexAsc(document.getId())) {
                chunk.setEmbedding(axis(axis), profile, Instant.now());
                chunks.saveAndFlush(chunk);
            }
        }
    }

    private static float[] axis(int index) {
        float[] values = new float[768];
        values[index] = 1;
        return values;
    }
}
