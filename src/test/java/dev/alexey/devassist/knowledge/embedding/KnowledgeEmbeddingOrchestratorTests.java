package dev.alexey.devassist.knowledge.embedding;

import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingResponseException;
import dev.alexey.devassist.knowledge.embedding.infrastructure.EmbeddingProperties;
import dev.alexey.devassist.knowledge.embedding.persistence.KnowledgeEmbeddingWriter;
import dev.alexey.devassist.knowledge.exception.KnowledgeBaseNotFoundException;
import dev.alexey.devassist.knowledge.repository.KnowledgeBaseRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeChunkRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeEmbeddingOrchestratorTests {
    private final UUID baseId = UUID.randomUUID();
    private final EmbeddingProfile profile = new EmbeddingProfile("openai", "model", 768);
    private final KnowledgeBaseRepository bases = mock(KnowledgeBaseRepository.class);
    private final KnowledgeChunkRepository chunks = mock(KnowledgeChunkRepository.class);
    private final KnowledgeEmbeddingGateway gateway = mock(KnowledgeEmbeddingGateway.class);
    private final KnowledgeEmbeddingWriter writer = mock(KnowledgeEmbeddingWriter.class);
    private final AtomicLong time = new AtomicLong(1_000_000);

    private KnowledgeEmbeddingOrchestrator orchestrator(int batchSize, int limit) {
        EmbeddingProperties properties = new EmbeddingProperties("openai", "model", "nomic", 768,
                Duration.ofSeconds(2), batchSize, limit);
        return new KnowledgeEmbeddingOrchestrator(bases, chunks, gateway, writer, profile,
                properties, () -> time.getAndAdd(5_000_000));
    }

    private KnowledgeChunkEmbeddingCandidate candidate(int index) {
        return new KnowledgeChunkEmbeddingCandidate(UUID.randomUUID(), baseId, "text " + index, "hash " + index);
    }

    private KnowledgeEmbeddingResult response(KnowledgeEmbeddingRequest request, Integer tokens, int milliseconds) {
        List<KnowledgeEmbeddingVector> vectors = new ArrayList<>();
        for (int index = 0; index < request.texts().size(); index++) {
            float[] values = new float[768];
            Arrays.fill(values, index + 1);
            vectors.add(new KnowledgeEmbeddingVector(values));
        }
        return new KnowledgeEmbeddingResult(vectors, profile.provider(), profile.model(), 768,
                tokens, Duration.ofMillis(milliseconds));
    }

    @Test void emptyBaseDoesNotCallProvider() {
        when(bases.existsById(baseId)).thenReturn(true);
        KnowledgeEmbeddingRunResult result = orchestrator(2, 3).embedKnowledgeBase(baseId);
        assertThat(result.discoveredCount()).isZero();
        assertThat(result.attemptedCount()).isZero();
        assertThat(result.batchCount()).isZero();
        assertThat(result.inputTokens()).isNull();
        assertThat(result.providerLatencyMs()).isZero();
        assertThat(result.totalRunLatencyMs()).isEqualTo(5);
        assertThat(result.limitReached()).isFalse();
        verifyNoInteractions(gateway, writer);
    }

    @Test void batchesRespectLimitPurposeAndPositionWithMetrics() {
        when(bases.existsById(baseId)).thenReturn(true);
        List<KnowledgeChunkEmbeddingCandidate> candidates = List.of(candidate(0), candidate(1), candidate(2), candidate(3));
        when(chunks.findEmbeddingCandidates(eq(baseId), eq("openai"), eq("model"), eq(768), any(Pageable.class)))
                .thenReturn(candidates);
        when(gateway.embed(any())).thenAnswer(invocation -> response(invocation.getArgument(0), 7, 11));
        when(writer.write(any(), any(), eq(profile))).thenReturn(KnowledgeEmbeddingWriter.Outcome.EMBEDDED);
        KnowledgeEmbeddingRunResult result = orchestrator(2, 3).embedKnowledgeBase(baseId);
        assertThat(result.discoveredCount()).isEqualTo(3);
        assertThat(result.attemptedCount()).isEqualTo(3);
        assertThat(result.embeddedCount()).isEqualTo(3);
        assertThat(result.batchCount()).isEqualTo(2);
        assertThat(result.inputTokens()).isEqualTo(14);
        assertThat(result.providerLatencyMs()).isEqualTo(22);
        assertThat(result.limitReached()).isTrue();
        var requests = org.mockito.ArgumentCaptor.forClass(KnowledgeEmbeddingRequest.class);
        verify(gateway, times(2)).embed(requests.capture());
        assertThat(requests.getAllValues()).extracting(KnowledgeEmbeddingRequest::purpose)
                .containsOnly(EmbeddingPurpose.DOCUMENT);
        assertThat(requests.getAllValues().get(0).texts()).containsExactly("text 0", "text 1");
        assertThat(requests.getAllValues().get(1).texts()).containsExactly("text 2");
        var page = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(chunks).findEmbeddingCandidates(eq(baseId), eq("openai"), eq("model"), eq(768), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(4);
        var vector = org.mockito.ArgumentCaptor.forClass(KnowledgeEmbeddingVector.class);
        verify(writer, times(3)).write(any(), vector.capture(), eq(profile));
        assertThat(vector.getAllValues()).extracting(value -> value.values()[0]).containsExactly(1f, 2f, 1f);
    }

    @Test void unknownBaseFailsBeforeProvider() {
        assertThatThrownBy(() -> orchestrator(2, 3).embedKnowledgeBase(baseId))
                .isInstanceOf(KnowledgeBaseNotFoundException.class);
        verifyNoInteractions(gateway, writer, chunks);
    }

    @Test void unknownTokenCountRemainsUnknownAcrossBatches() {
        when(bases.existsById(baseId)).thenReturn(true);
        when(chunks.findEmbeddingCandidates(any(), any(), any(), anyInt(), any()))
                .thenReturn(List.of(candidate(0), candidate(1)));
        when(gateway.embed(any())).thenAnswer(invocation -> {
            KnowledgeEmbeddingRequest request = invocation.getArgument(0);
            return response(request, request.texts().getFirst().equals("text 0") ? 5 : null, 1);
        });
        when(writer.write(any(), any(), eq(profile))).thenReturn(KnowledgeEmbeddingWriter.Outcome.EMBEDDED);
        KnowledgeEmbeddingRunResult result = orchestrator(1, 2).embedKnowledgeBase(baseId);
        assertThat(result.inputTokens()).isNull();
        assertThat(result.batchCount()).isEqualTo(2);
    }

    @Test void badResponseRejectsWholeBatchBeforeWriter() {
        when(bases.existsById(baseId)).thenReturn(true);
        when(chunks.findEmbeddingCandidates(any(), any(), any(), anyInt(), any()))
                .thenReturn(List.of(candidate(0), candidate(1)));
        when(gateway.embed(any())).thenAnswer(invocation -> response(
                new KnowledgeEmbeddingRequest(List.of("only one"), EmbeddingPurpose.DOCUMENT), 1, 1));
        KnowledgeEmbeddingOrchestrator service = orchestrator(2, 2);
        assertThatThrownBy(() -> service.embedKnowledgeBase(baseId))
                .isInstanceOf(KnowledgeEmbeddingResponseException.class);
        verifyNoInteractions(writer);
    }

    @Test void failureStopsLaterBatches() {
        when(bases.existsById(baseId)).thenReturn(true);
        when(chunks.findEmbeddingCandidates(any(), any(), any(), anyInt(), any()))
                .thenReturn(List.of(candidate(0), candidate(1)));
        when(gateway.embed(any())).thenThrow(new KnowledgeEmbeddingResponseException());
        KnowledgeEmbeddingOrchestrator service = orchestrator(1, 2);
        assertThatThrownBy(() -> service.embedKnowledgeBase(baseId))
                .isInstanceOf(KnowledgeEmbeddingResponseException.class);
        verify(gateway).embed(any());
        verifyNoInteractions(writer);
    }
}
