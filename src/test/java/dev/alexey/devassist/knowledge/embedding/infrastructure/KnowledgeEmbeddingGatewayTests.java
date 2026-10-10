package dev.alexey.devassist.knowledge.embedding.infrastructure;

import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import dev.alexey.devassist.knowledge.embedding.EmbeddingPurpose;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingGateway;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingRequest;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingResult;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingVector;
import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingException;
import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingResponseException;
import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingTimeoutException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgeEmbeddingGatewayTests {
    private final EmbeddingModel model = mock(EmbeddingModel.class);
    private final AtomicLong clock = new AtomicLong(1_000_000);

    private KnowledgeEmbeddingGateway gateway(String provider) {
        EmbeddingProfile profile = new EmbeddingProfile(provider, "configured-model", 768);
        return "openai".equals(provider)
                ? new OpenAiKnowledgeEmbeddingGateway(model, profile, () -> clock.getAndAdd(2_000_000))
                : new OllamaKnowledgeEmbeddingGateway(model, profile, () -> clock.getAndAdd(2_000_000));
    }
    private float[] vector(float value) {
        float[] values = new float[768];
        Arrays.fill(values, value);
        return values;
    }
    private EmbeddingResponse response(List<Embedding> vectors) {
        return new EmbeddingResponse(vectors,
                new EmbeddingResponseMetadata("reported-model", new DefaultUsage(12, 0)));
    }

    @Test void bothAdaptersSendOneOrderedBatchAndReturnOrderedDefensiveVectors() {
        for (String provider : List.of("openai", "ollama")) {
            reset(model);
            float[] first = vector(0.1f);
            float[] second = vector(0.2f);
            when(model.call(any(EmbeddingRequest.class))).thenReturn(response(List.of(
                    new Embedding(first, 0), new Embedding(second, 1))));
            List<String> input = new ArrayList<>(List.of("first", "second"));
            KnowledgeEmbeddingRequest request = new KnowledgeEmbeddingRequest(input, EmbeddingPurpose.DOCUMENT);
            input.set(0, "changed");
            KnowledgeEmbeddingResult result = gateway(provider).embed(request);
            ArgumentCaptor<EmbeddingRequest> captor = ArgumentCaptor.forClass(EmbeddingRequest.class);
            verify(model, times(1)).call(captor.capture());
            assertThat(captor.getValue().getInstructions()).containsExactlyElementsOf(
                    "ollama".equals(provider) ? List.of("search_document: first", "search_document: second")
                            : List.of("first", "second"));
            assertThat(result.provider()).isEqualTo(provider);
            assertThat(result.model()).isEqualTo("reported-model");
            assertThat(result.dimensions()).isEqualTo(768);
            assertThat(result.inputTokens()).isEqualTo(12);
            assertThat(result.latency()).isEqualTo(Duration.ofMillis(2));
            assertThat(result.vectors()).hasSize(2);
            assertThat(result.vectors().get(0).values()[0]).isEqualTo(0.1f);
            assertThat(result.vectors().get(1).values()[0]).isEqualTo(0.2f);
            first[0] = 99f;
            float[] exposed = result.vectors().getFirst().values();
            exposed[0] = 99f;
            assertThat(result.vectors().getFirst().values()[0]).isEqualTo(0.1f);
            assertThatThrownBy(() -> result.vectors().clear()).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test void rejectsMissingAndBlankInputBeforeProviderCall() {
        assertThatThrownBy(() -> new KnowledgeEmbeddingRequest(List.of(), EmbeddingPurpose.DOCUMENT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KnowledgeEmbeddingRequest(List.of(" "), EmbeddingPurpose.DOCUMENT))
                .isInstanceOf(IllegalArgumentException.class);
        KnowledgeEmbeddingGateway gateway = gateway("openai");
        assertThatThrownBy(() -> gateway.embed(null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(model);
    }

    @Test void rejectsMissingMismatchedOrMalformedResponses() {
        KnowledgeEmbeddingGateway gateway = gateway("openai");
        KnowledgeEmbeddingRequest request = new KnowledgeEmbeddingRequest(List.of("one", "two"), EmbeddingPurpose.DOCUMENT);
        when(model.call(any(EmbeddingRequest.class))).thenReturn(null);
        assertThatThrownBy(() -> gateway.embed(request)).isInstanceOf(KnowledgeEmbeddingResponseException.class);
        when(model.call(any(EmbeddingRequest.class))).thenReturn(response(List.of(new Embedding(vector(1f), 0))));
        assertThatThrownBy(() -> gateway.embed(request)).isInstanceOf(KnowledgeEmbeddingResponseException.class);
        when(model.call(any(EmbeddingRequest.class))).thenReturn(response(List.of(
                new Embedding(vector(1f), 1), new Embedding(vector(2f), 0))));
        assertThatThrownBy(() -> gateway.embed(request)).isInstanceOf(KnowledgeEmbeddingResponseException.class);
    }

    @Test void rejectsWrongDimensionsAndNonFiniteVectors() {
        KnowledgeEmbeddingGateway gateway = gateway("ollama");
        KnowledgeEmbeddingRequest request = new KnowledgeEmbeddingRequest(List.of("one"), EmbeddingPurpose.DOCUMENT);
        when(model.call(any(EmbeddingRequest.class))).thenReturn(response(List.of(new Embedding(new float[3], 0))));
        assertThatThrownBy(() -> gateway.embed(request)).isInstanceOf(KnowledgeEmbeddingResponseException.class);
        float[] nan = vector(1f);
        nan[0] = Float.NaN;
        when(model.call(any(EmbeddingRequest.class))).thenReturn(response(List.of(new Embedding(nan, 0))));
        assertThatThrownBy(() -> gateway.embed(request)).isInstanceOf(KnowledgeEmbeddingResponseException.class);
        float[] infinity = vector(1f);
        infinity[0] = Float.POSITIVE_INFINITY;
        when(model.call(any(EmbeddingRequest.class))).thenReturn(response(List.of(new Embedding(infinity, 0))));
        assertThatThrownBy(() -> gateway.embed(request)).isInstanceOf(KnowledgeEmbeddingResponseException.class);
        when(model.call(any(EmbeddingRequest.class))).thenReturn(response(List.of(new Embedding(new float[768], 0))));
        assertThatThrownBy(() -> gateway.embed(request)).isInstanceOf(KnowledgeEmbeddingResponseException.class);
    }

    @Test void mapsTimeoutAndGenericFailureToSafeMessages() {
        KnowledgeEmbeddingGateway gateway = gateway("openai");
        KnowledgeEmbeddingRequest request = new KnowledgeEmbeddingRequest(List.of("private chunk"), EmbeddingPurpose.DOCUMENT);
        when(model.call(any(EmbeddingRequest.class))).thenThrow(new RuntimeException(
                new SocketTimeoutException("private chunk and internal URL")));
        assertThatThrownBy(() -> gateway.embed(request)).isInstanceOf(KnowledgeEmbeddingTimeoutException.class)
                .hasMessage("Embedding provider timed out");
        when(model.call(any(EmbeddingRequest.class))).thenThrow(new IllegalStateException("private chunk and raw response"));
        assertThatThrownBy(() -> gateway.embed(request)).isInstanceOf(KnowledgeEmbeddingException.class)
                .hasMessage("Embedding provider unavailable");
    }

    @Test void missingResponseMetadataUsesConfiguredModelAndNullableUsage() {
        when(model.call(any(EmbeddingRequest.class))).thenReturn(new EmbeddingResponse(
                List.of(new Embedding(vector(1f), 0))));
        KnowledgeEmbeddingResult result = gateway("ollama").embed(new KnowledgeEmbeddingRequest(List.of("text"), EmbeddingPurpose.DOCUMENT));
        assertThat(result.model()).isEqualTo("configured-model");
        assertThat(result.inputTokens()).isNull();
    }

    @Test void ollamaZeroUsageIsReportedAsUnknown() {
        when(model.call(any(EmbeddingRequest.class))).thenReturn(new EmbeddingResponse(
                List.of(new Embedding(vector(1f), 0)),
                new EmbeddingResponseMetadata("nomic-embed-text", new DefaultUsage(0, 0))));
        KnowledgeEmbeddingResult result = gateway("ollama").embed(new KnowledgeEmbeddingRequest(List.of("text"), EmbeddingPurpose.DOCUMENT));
        assertThat(result.inputTokens()).isNull();
    }

    @Test void profileRequiresProviderModelAndDimensionsToMatch() {
        EmbeddingProfile profile = new EmbeddingProfile("openai", "model", 768);
        assertThat(profile.matches("openai", "model", 768)).isTrue();
        assertThat(profile.matches("ollama", "model", 768)).isFalse();
        assertThat(profile.matches("openai", "other", 768)).isFalse();
        assertThat(profile.matches("openai", "model", null)).isFalse();
    }

    @Test void queryPurposeIsTranslatedOnlyForOllamaWithoutMutatingInput() {
        when(model.call(any(EmbeddingRequest.class))).thenReturn(response(List.of(new Embedding(vector(1f), 0))));
        KnowledgeEmbeddingRequest request = new KnowledgeEmbeddingRequest(List.of("question"), EmbeddingPurpose.QUERY);
        gateway("ollama").embed(request);
        var captor = ArgumentCaptor.forClass(EmbeddingRequest.class);
        verify(model).call(captor.capture());
        assertThat(captor.getValue().getInstructions()).containsExactly("search_query: question");
        assertThat(request.texts()).containsExactly("question");
        clearInvocations(model);
        gateway("openai").embed(request);
        verify(model).call(captor.capture());
        assertThat(captor.getValue().getInstructions()).containsExactly("question");
    }
}
