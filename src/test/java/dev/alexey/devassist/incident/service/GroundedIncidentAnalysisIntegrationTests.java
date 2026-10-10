package dev.alexey.devassist.incident.service;

import dev.alexey.devassist.analysis.IncidentAnalysisGateway;
import dev.alexey.devassist.analysis.IncidentAnalysisInput;
import dev.alexey.devassist.analysis.IncidentAnalysisResult;
import dev.alexey.devassist.analysis.model.StructuredIncidentAnalysis;
import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import dev.alexey.devassist.knowledge.embedding.EmbeddingPurpose;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingGateway;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingResult;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingVector;
import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingTimeoutException;
import dev.alexey.devassist.knowledge.entity.KnowledgeBase;
import dev.alexey.devassist.knowledge.entity.KnowledgeChunk;
import dev.alexey.devassist.knowledge.entity.KnowledgeDocument;
import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import dev.alexey.devassist.knowledge.repository.KnowledgeBaseRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeChunkRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeDocumentRepository;
import dev.alexey.devassist.incident.entity.Incident;
import dev.alexey.devassist.incident.enums.IncidentSource;
import dev.alexey.devassist.incident.repository.IncidentRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static dev.alexey.devassist.analysis.StructuredAnalysisFixtures.analysis;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {"app.ai.provider=ollama", "app.rag.enabled=true",
        "app.rag.embedding.provider=ollama"})
class GroundedIncidentAnalysisIntegrationTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg17-bookworm")
                    .asCompatibleSubstituteFor("postgres"));
    @Autowired IncidentAnalysisService service;
    @Autowired WebApplicationContext webContext;
    MockMvc mvc;
    @Autowired IncidentRepository incidents;
    @Autowired KnowledgeBaseRepository bases;
    @Autowired KnowledgeDocumentRepository documents;
    @Autowired KnowledgeChunkRepository chunks;
    @Autowired EmbeddingProfile profile;
    @MockitoBean KnowledgeEmbeddingGateway embedding;
    @MockitoBean IncidentAnalysisGateway chat;

    @BeforeEach
    void setUpMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(webContext).build();
    }

    @Test
    void retrievalAndBothChatAttemptsRunOutsideTransactionWithoutDatabaseWrites() throws Exception {
        var base = bases.saveAndFlush(new KnowledgeBase("audit-" + UUID.randomUUID(), "Audit", null));
        var document = documents.saveAndFlush(new KnowledgeDocument(base, "runbook", "Runbook",
                KnowledgeSourceType.RUNBOOK, null, "a".repeat(64)));
        var chunk = new KnowledgeChunk(document, 0, "Evidence", "b".repeat(64), Map.of("section", "one"));
        chunk.setEmbedding(vector(), profile, Instant.now());
        chunk = chunks.saveAndFlush(chunk);
        var incident = new Incident("Database error", "Connection failed", IncidentSource.API);
        incident.assignKnowledgeBase(base);
        incident = incidents.saveAndFlush(incident);
        UUID incidentId = incident.getId();
        UUID chunkId = chunk.getId();
        var baseline = incidents.findById(incidentId).orElseThrow();
        Instant createdAt = baseline.getCreatedAt();
        Instant updatedAt = baseline.getUpdatedAt();
        long documentCount = documents.count();
        long chunkCount = chunks.count();
        when(embedding.embed(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(invocation.getArgument(0, dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingRequest.class)
                    .purpose()).isEqualTo(EmbeddingPurpose.QUERY);
            return new KnowledgeEmbeddingResult(List.of(new KnowledgeEmbeddingVector(vector())),
                    profile.provider(), profile.model(), 768, 7, Duration.ofMillis(4));
        });
        var calls = new AtomicInteger();
        when(chat.analyze(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            IncidentAnalysisInput input = invocation.getArgument(0);
            assertThat(input.context().sources()).hasSize(1);
            assertThat(input.context().sources().getFirst().sourceId()).isEqualTo("S1");
            var ids = calls.getAndIncrement() == 0 ? List.of("S999") : List.of("S1");
            var original = analysis();
            var cited = new StructuredIncidentAnalysis(original.summary(), original.probableCauses(),
                    original.investigationSteps(), original.uncertainties(), ids);
            return new IncidentAnalysisResult(cited, "fake", "model", "incident-analysis-v3",
                    Instant.EPOCH, 10, 10, 5, 15, 1);
        });
        var response = mvc.perform(post("/api/v1/incidents/{id}/analysis", incidentId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var json = tools.jackson.databind.json.JsonMapper.shared().readTree(response);
        assertThat(json.path("groundingStatus").asString()).isEqualTo("CONTEXT_PROVIDED");
        assertThat(json.path("grounded").asBoolean()).isTrue();
        assertThat(json.path("knowledgeBaseId").asString()).isEqualTo(base.getId().toString());
        assertThat(json.at("/citations/0/chunkId").asString()).isEqualTo(chunkId.toString());
        assertThat(json.at("/citations/0/documentTitle").asString()).isEqualTo("Runbook");
        assertThat(json.path("attemptCount").asInt()).isEqualTo(2);
        assertThat(json.path("retrievalInputTokens").asInt()).isEqualTo(7);
        assertThat(json.path("inputTokens").asInt()).isEqualTo(20);
        assertThat(json.path("outputTokens").asInt()).isEqualTo(10);
        assertThat(json.path("retrievedChunkCount").asInt()).isEqualTo(1);
        assertThat(json.path("providedContextChunkCount").asInt()).isEqualTo(1);
        assertThat(json.path("providedContextCharacters").asInt()).isEqualTo(8);
        assertThat(json.path("retrievalLatencyMs").asLong()).isGreaterThanOrEqualTo(0);
        assertThat(response).doesNotContain("Evidence", "embedding", "vector");
        verify(embedding, times(1)).embed(any());
        verify(chat, times(2)).analyze(any());
        var after = incidents.findById(incidentId).orElseThrow();
        assertThat(after.getCreatedAt()).isEqualTo(createdAt);
        assertThat(after.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(after.getKnowledgeBase().getId()).isEqualTo(base.getId());
        assertThat(documents.count()).isEqualTo(documentCount);
        assertThat(chunks.count()).isEqualTo(chunkCount);
        assertThat(chunks.findById(chunkId).orElseThrow().getContent()).isEqualTo("Evidence");
    }

    @Test
    void embeddingTimeoutFallsBackToOrdinaryAnalysisWithoutDatabaseWrites() {
        var base = bases.saveAndFlush(new KnowledgeBase("timeout-" + UUID.randomUUID(), "Audit", null));
        var document = documents.saveAndFlush(new KnowledgeDocument(base, "runbook", "Runbook",
                KnowledgeSourceType.RUNBOOK, null, "a".repeat(64)));
        var chunk = new KnowledgeChunk(document, 0, "Evidence", "b".repeat(64), Map.of());
        chunk.setEmbedding(vector(), profile, Instant.now());
        chunks.saveAndFlush(chunk);
        var incident = new Incident("Error", "Description", IncidentSource.API);
        incident.assignKnowledgeBase(base);
        incident = incidents.saveAndFlush(incident);
        UUID incidentId = incident.getId();
        Instant incidentUpdatedAt = incidents.findById(incidentId).orElseThrow().getUpdatedAt();
        long baseCount = bases.count();
        long documentCount = documents.count();
        long chunkCount = chunks.count();
        when(embedding.embed(any())).thenThrow(new KnowledgeEmbeddingTimeoutException());
        when(chat.analyze(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            IncidentAnalysisInput input = invocation.getArgument(0);
            assertThat(input.context().sources()).isEmpty();
            return new IncidentAnalysisResult(analysis(), "fake", "model", "incident-analysis-v3",
                    Instant.EPOCH, 10, 10, 5, 15, 1);
        });
        var response = service.analyze(incidentId);
        assertThat(response.groundingStatus()).isEqualTo(
                dev.alexey.devassist.analysis.grounding.IncidentGroundingStatus.RETRIEVAL_FAILED);
        assertThat(response.grounded()).isFalse();
        assertThat(response.citations()).isEmpty();
        assertThat(response.retrievalInputTokens()).isNull();
        verify(embedding, times(1)).embed(any());
        verify(chat, times(1)).analyze(any());
        assertThat(incidents.existsById(incidentId)).isTrue();
        assertThat(incidents.findById(incidentId).orElseThrow().getUpdatedAt()).isEqualTo(incidentUpdatedAt);
        assertThat(bases.count()).isEqualTo(baseCount);
        assertThat(documents.count()).isEqualTo(documentCount);
        assertThat(chunks.count()).isEqualTo(chunkCount);
    }

    @Test
    void emptyKnowledgeBaseSkipsEmbeddingAndUsesOrdinaryAnalysis() {
        var base = bases.saveAndFlush(new KnowledgeBase("empty-" + UUID.randomUUID(), "Empty", null));
        var incident = new Incident("Error", "Description", IncidentSource.API);
        incident.assignKnowledgeBase(base);
        UUID id = incidents.saveAndFlush(incident).getId();
        when(chat.analyze(any())).thenReturn(new IncidentAnalysisResult(analysis(), "fake", "model",
                "incident-analysis-v3", Instant.EPOCH, 10, 5, 3, 8, 1));
        var response = service.analyze(id);
        assertThat(response.groundingStatus()).isEqualTo(
                dev.alexey.devassist.analysis.grounding.IncidentGroundingStatus.NO_RELEVANT_CONTEXT);
        assertThat(response.citations()).isEmpty();
        assertThat(response.grounded()).isFalse();
        assertThat(response.retrievalInputTokens()).isNull();
        verifyNoInteractions(embedding);
        verify(chat, times(1)).analyze(any());
    }

    @Test
    void belowThresholdSearchStillEmbedsButProvidesNoContext() {
        var base = bases.saveAndFlush(new KnowledgeBase("threshold-" + UUID.randomUUID(), "Threshold", null));
        var document = documents.saveAndFlush(new KnowledgeDocument(base, "runbook", "Runbook",
                KnowledgeSourceType.RUNBOOK, null, "a".repeat(64)));
        var chunk = new KnowledgeChunk(document, 0, "Evidence", "b".repeat(64), Map.of());
        chunk.setEmbedding(vector(), profile, Instant.now());
        chunks.saveAndFlush(chunk);
        var incident = new Incident("Error", "Description", IncidentSource.API);
        incident.assignKnowledgeBase(base);
        UUID id = incidents.saveAndFlush(incident).getId();
        when(embedding.embed(any())).thenReturn(new KnowledgeEmbeddingResult(
                List.of(new KnowledgeEmbeddingVector(otherVector())),
                profile.provider(), profile.model(), 768, 3, Duration.ofMillis(2)));
        when(chat.analyze(any())).thenAnswer(invocation -> {
            IncidentAnalysisInput input = invocation.getArgument(0);
            assertThat(input.context().sources()).isEmpty();
            return new IncidentAnalysisResult(analysis(), "fake", "model", "incident-analysis-v3",
                    Instant.EPOCH, 10, 5, 3, 8, 1);
        });
        var response = service.analyze(id);
        assertThat(response.groundingStatus()).isEqualTo(
                dev.alexey.devassist.analysis.grounding.IncidentGroundingStatus.NO_RELEVANT_CONTEXT);
        assertThat(response.retrievalInputTokens()).isEqualTo(3);
        assertThat(response.retrievedChunkCount()).isZero();
        assertThat(response.citations()).isEmpty();
        verify(embedding, times(1)).embed(any());
        verify(chat, times(1)).analyze(any());
    }

    private static float[] otherVector() {
        float[] values = new float[768];
        values[1] = 1;
        return values;
    }

    private static float[] vector() {
        float[] values = new float[768];
        values[0] = 1;
        return values;
    }
}
