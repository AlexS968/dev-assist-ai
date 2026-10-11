package dev.alexey.devassist.knowledge.retrieval;

import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import dev.alexey.devassist.knowledge.embedding.EmbeddingPurpose;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingGateway;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingRequest;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingResult;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingVector;
import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingResponseException;
import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingTimeoutException;
import dev.alexey.devassist.knowledge.exception.KnowledgeBaseNotFoundException;
import dev.alexey.devassist.knowledge.repository.KnowledgeBaseRepository;
import dev.alexey.devassist.knowledge.retrieval.persistence.KnowledgeVectorSearchRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeRetrievalServiceTests {
    private final UUID baseId = UUID.randomUUID();
    private final EmbeddingProfile profile = new EmbeddingProfile("ollama", "nomic", 768);
    private final KnowledgeBaseRepository bases = mock(KnowledgeBaseRepository.class);
    private final KnowledgeVectorSearchRepository search = mock(KnowledgeVectorSearchRepository.class);
    private final KnowledgeEmbeddingGateway gateway = mock(KnowledgeEmbeddingGateway.class);
    private final AtomicLong time = new AtomicLong();
    private final RetrievalProperties properties = new RetrievalProperties(5, 20, 0.60, 0.70);

    private KnowledgeRetrievalService service() {
        return new KnowledgeRetrievalService(bases, search, gateway, profile, properties,
                () -> time.getAndAdd(2_000_000));
    }

    private KnowledgeRetrievalRequest request(Integer topK, Double threshold) {
        return new KnowledgeRetrievalRequest(baseId, "private query", topK, threshold);
    }

    private KnowledgeEmbeddingResult response(String provider, String model, int count) {
        float[] values = new float[768];
        values[0] = 1;
        List<KnowledgeEmbeddingVector> vectors = java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new KnowledgeEmbeddingVector(values)).toList();
        return new KnowledgeEmbeddingResult(vectors, provider, model, 768, null, Duration.ofMillis(4));
    }

    @Test void requestValidationDoesNotExposeQuery() {
        assertThatThrownBy(() -> new KnowledgeRetrievalRequest(null, "secret", null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("secret");
        assertThatThrownBy(() -> new KnowledgeRetrievalRequest(baseId, " ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        String longQuery = "x".repeat(10001);
        assertThatThrownBy(() -> new KnowledgeRetrievalRequest(baseId, longQuery, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request(0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request(null, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> request(null, 1.1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void missingBaseStopsBeforeProviderAndSearch() {
        KnowledgeRetrievalService retrieval = service();
        KnowledgeRetrievalRequest query = request(null, null);
        assertThatThrownBy(() -> retrieval.retrieve(query))
                .isInstanceOf(KnowledgeBaseNotFoundException.class);
        verifyNoInteractions(gateway, search);
    }

    @Test void noCompatibleEmbeddingsReturnsEmptyWithMeasuredTotal() {
        when(bases.existsById(baseId)).thenReturn(true);
        KnowledgeRetrievalResult result = service().retrieve(request(null, null));
        assertThat(result.matches()).isEmpty();
        assertThat(result.inputTokens()).isNull();
        assertThat(result.embeddingLatencyMs()).isZero();
        assertThat(result.searchLatencyMs()).isZero();
        assertThat(result.totalLatencyMs()).isEqualTo(2);
        verifyNoInteractions(gateway);
    }

    @Test void queryPurposeOverridesAndMetricsAreCorrect() {
        when(bases.existsById(baseId)).thenReturn(true);
        when(search.hasCompatibleEmbeddings(baseId, profile)).thenReturn(true);
        when(gateway.embed(any())).thenReturn(response("ollama", "nomic", 1));
        KnowledgeRetrievalResult result = service().retrieve(request(3, 0.8));
        var captor = org.mockito.ArgumentCaptor.forClass(KnowledgeEmbeddingRequest.class);
        verify(gateway).embed(captor.capture());
        assertThat(captor.getValue().texts()).containsExactly("private query");
        assertThat(captor.getValue().purpose()).isEqualTo(EmbeddingPurpose.QUERY);
        verify(search).search(eq(baseId), eq(profile), any(float[].class), eq(3), eq(0.8));
        assertThat(result.matches()).isEmpty();
        assertThat(result.inputTokens()).isNull();
        assertThat(result.embeddingLatencyMs()).isEqualTo(4);
        assertThat(result.searchLatencyMs()).isEqualTo(2);
        assertThat(result.totalLatencyMs()).isEqualTo(6);
    }

    @Test void defaultsAndMaximumAreApplied() {
        when(bases.existsById(baseId)).thenReturn(true);
        when(search.hasCompatibleEmbeddings(baseId, profile)).thenReturn(true);
        when(gateway.embed(any())).thenReturn(response("ollama", "nomic", 1));
        service().retrieve(request(null, null));
        verify(search).search(eq(baseId), eq(profile), any(float[].class), eq(5), eq(0.70));
        KnowledgeRetrievalService retrieval = service();
        KnowledgeRetrievalRequest excessiveTopK = request(21, null);
        assertThatThrownBy(() -> retrieval.retrieve(excessiveTopK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void openAiProfileUsesOpenAiThresholdAndRequestOverrideWins() {
        EmbeddingProfile openAiProfile = new EmbeddingProfile("openai", "text-embedding-3-small", 768);
        when(bases.existsById(baseId)).thenReturn(true);
        when(search.hasCompatibleEmbeddings(baseId, openAiProfile)).thenReturn(true);
        when(gateway.embed(any())).thenReturn(response("openai", "text-embedding-3-small", 1));
        var retrieval = new KnowledgeRetrievalService(bases, search, gateway, openAiProfile,
                properties, () -> time.getAndAdd(2_000_000));
        retrieval.retrieve(request(null, null));
        verify(search).search(eq(baseId), eq(openAiProfile), any(float[].class), eq(5), eq(0.60));
        retrieval.retrieve(request(null, 0.85));
        verify(search).search(eq(baseId), eq(openAiProfile), any(float[].class), eq(5), eq(0.85));
    }

    @Test void providerFailureAndInvalidResponseNeverSearch() {
        when(bases.existsById(baseId)).thenReturn(true);
        when(search.hasCompatibleEmbeddings(baseId, profile)).thenReturn(true);
        KnowledgeRetrievalService retrieval = service();
        KnowledgeRetrievalRequest query = request(null, null);
        when(gateway.embed(any())).thenThrow(new KnowledgeEmbeddingTimeoutException());
        assertThatThrownBy(() -> retrieval.retrieve(query))
                .isInstanceOf(KnowledgeEmbeddingTimeoutException.class);
        doReturn(response("ollama", "nomic", 2)).when(gateway).embed(any());
        assertThatThrownBy(() -> retrieval.retrieve(query))
                .isInstanceOf(KnowledgeEmbeddingResponseException.class);
        doReturn(response("openai", "nomic", 1)).when(gateway).embed(any());
        assertThatThrownBy(() -> retrieval.retrieve(query))
                .isInstanceOf(KnowledgeEmbeddingResponseException.class);
        verify(search, never()).search(any(), any(), any(), anyInt(), anyDouble());
    }

    @Test void resultAndMetadataAreImmutable() {
        var match = new RetrievedKnowledgeChunk(UUID.randomUUID(), UUID.randomUUID(), "external", "Title",
                dev.alexey.devassist.knowledge.enums.KnowledgeSourceType.RUNBOOK, null, 0,
                "untrusted", Map.of("section", "one"), 0.9);
        var mutable = new java.util.ArrayList<>(List.of(match));
        var result = new KnowledgeRetrievalResult(baseId, profile, mutable, 1, 2, 3, 4);
        mutable.clear();
        assertThat(result.matches()).hasSize(1);
        List<RetrievedKnowledgeChunk> immutableMatches = result.matches();
        assertThatThrownBy(immutableMatches::clear).isInstanceOf(UnsupportedOperationException.class);
        Map<String, String> immutableMetadata = match.metadata();
        assertThatThrownBy(() -> immutableMetadata.put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void zeroVectorIsRejectedAndLargeFiniteVectorHasSafeNorm() {
        float[] zeros = new float[768];
        assertThatThrownBy(() -> new KnowledgeEmbeddingVector(zeros)).isInstanceOf(IllegalArgumentException.class);
        float[] huge = new float[768];
        java.util.Arrays.fill(huge, Float.MAX_VALUE);
        assertThat(new KnowledgeEmbeddingVector(huge).values()).hasSize(768);
    }
}
