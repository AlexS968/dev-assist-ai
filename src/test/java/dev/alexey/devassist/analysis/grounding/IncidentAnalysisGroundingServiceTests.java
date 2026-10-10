package dev.alexey.devassist.analysis.grounding;

import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import dev.alexey.devassist.knowledge.retrieval.KnowledgeRetrievalRequest;
import dev.alexey.devassist.knowledge.retrieval.KnowledgeRetrievalResult;
import dev.alexey.devassist.knowledge.retrieval.KnowledgeRetrievalService;
import dev.alexey.devassist.knowledge.retrieval.RetrievedKnowledgeChunk;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class IncidentAnalysisGroundingServiceTests {
    private final KnowledgeRetrievalService retrieval = mock(KnowledgeRetrievalService.class);
    private final GroundingProperties limits = new GroundingProperties(6000, 5);

    @Test
    void disabledAndUnlinkedIncidentsDoNotRetrieve() {
        UUID id = UUID.randomUUID();
        var disabled = new IncidentAnalysisGroundingService(null, null, false);
        assertThat(disabled.prepare(id, "title", "description").status())
                .isEqualTo(IncidentGroundingStatus.RAG_DISABLED);
        var enabled = new IncidentAnalysisGroundingService(retrieval, limits, true);
        assertThat(enabled.prepare(null, "title", "description").status())
                .isEqualTo(IncidentGroundingStatus.NO_KNOWLEDGE_BASE);
        verifyNoInteractions(retrieval);
    }

    @Test
    void successfulRetrievalSuppliesContextAndMetricsOnce() {
        UUID id = UUID.randomUUID();
        var chunk = new RetrievedKnowledgeChunk(UUID.randomUUID(), UUID.randomUUID(), "ext", "Runbook",
                KnowledgeSourceType.RUNBOOK, null, 0, "evidence", Map.of(), 0.91);
        var result = new KnowledgeRetrievalResult(id, new EmbeddingProfile("ollama", "model", 768),
                List.of(chunk), 13, 4, 3, 9);
        when(retrieval.retrieve(any(KnowledgeRetrievalRequest.class))).thenReturn(result);
        var decision = new IncidentAnalysisGroundingService(retrieval, limits, true)
                .prepare(id, "title", "description");
        assertThat(decision.status()).isEqualTo(IncidentGroundingStatus.CONTEXT_PROVIDED);
        assertThat(decision.context().sources()).hasSize(1);
        assertThat(decision.retrievalLatencyMs()).isEqualTo(9);
        assertThat(decision.retrievalInputTokens()).isEqualTo(13);
        verify(retrieval, times(1)).retrieve(argThat(request -> request.knowledgeBaseId().equals(id)
                && request.query().equals("title\ndescription")));
    }

    @Test
    void emptyCompatibleCorpusReturnsNoRelevantContext() {
        UUID id = UUID.randomUUID();
        var empty = new KnowledgeRetrievalResult(id, new EmbeddingProfile("ollama", "model", 768),
                List.of(), null, 0, 0, 2);
        when(retrieval.retrieve(any(KnowledgeRetrievalRequest.class))).thenReturn(empty);
        var decision = new IncidentAnalysisGroundingService(retrieval, limits, true)
                .prepare(id, "title", "description");
        assertThat(decision.status()).isEqualTo(IncidentGroundingStatus.NO_RELEVANT_CONTEXT);
        assertThat(decision.context().sources()).isEmpty();
        assertThat(decision.retrievalInputTokens()).isNull();
        assertThat(decision.retrievalLatencyMs()).isEqualTo(2);
        verify(retrieval, times(1)).retrieve(any(KnowledgeRetrievalRequest.class));
    }

    @ParameterizedTest
    @MethodSource("expectedFailures")
    void expectedRetrievalFailureFallsBackWithoutSensitiveLogging(RuntimeException failure, CapturedOutput output) {
        UUID id = UUID.randomUUID();
        when(retrieval.retrieve(any(KnowledgeRetrievalRequest.class))).thenThrow(failure);
        var decision = new IncidentAnalysisGroundingService(retrieval, limits, true)
                .prepare(id, "PRIVATE query", "PRIVATE description");
        assertThat(decision.status()).isEqualTo(IncidentGroundingStatus.RETRIEVAL_FAILED);
        assertThat(decision.context().sources()).isEmpty();
        assertThat(decision.retrievalInputTokens()).isNull();
        assertThat(output.getOut()).contains("Incident grounding retrieval failed")
                .doesNotContain("PRIVATE", "secret provider URL", "secret SQL");
        verify(retrieval, times(1)).retrieve(any(KnowledgeRetrievalRequest.class));
    }

    static Stream<RuntimeException> expectedFailures() {
        return Stream.of(
                new dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingTimeoutException(),
                new dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingException("secret provider URL"),
                new org.springframework.dao.DataAccessResourceFailureException("secret SQL"));
    }

    @Test
    void programmingErrorPropagates() {
        when(retrieval.retrieve(any(KnowledgeRetrievalRequest.class)))
                .thenThrow(new IllegalStateException("programming bug"));
        var service = new IncidentAnalysisGroundingService(retrieval, limits, true);
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.prepare(id, "title", "description"))
                .isInstanceOf(IllegalStateException.class);
    }
}
