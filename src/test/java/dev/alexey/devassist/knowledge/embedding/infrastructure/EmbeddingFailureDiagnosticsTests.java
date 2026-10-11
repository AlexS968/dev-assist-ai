package dev.alexey.devassist.knowledge.embedding.infrastructure;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.openai.core.http.Headers;
import com.openai.errors.BadRequestException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;
import com.openai.models.ErrorObject;
import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import dev.alexey.devassist.knowledge.embedding.EmbeddingPurpose;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingRequest;
import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingDiagnostic.Category;
import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingException;
import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingTimeoutException;
import java.net.SocketTimeoutException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmbeddingFailureDiagnosticsTests {
    private final EmbeddingModel model = mock(EmbeddingModel.class);
    private final KnowledgeEmbeddingRequest request = new KnowledgeEmbeddingRequest(
            List.of("PRIVATE_CHUNK_CONTENT"), EmbeddingPurpose.DOCUMENT);

    @Test void openAiSdkStatusAndAllowlistedCodeAreRetainedWithoutRawMessage() {
        var providerError = ErrorObject.builder().message("PRIVATE_CHUNK_CONTENT SECRET_KEY")
                .type("invalid_request_error").code("rate_limit_exceeded")
                .param(java.util.Optional.empty()).build();
        var failure = RateLimitException.builder().headers(Headers.builder()
                .put("Authorization", "SECRET_KEY").build()).error(providerError).build();
        var diagnostic = EmbeddingFailureDiagnostics.classify(new IllegalStateException(failure), "openai");
        assertThat(diagnostic.provider()).isEqualTo("openai");
        assertThat(diagnostic.category()).isEqualTo(Category.HTTP_ERROR);
        assertThat(diagnostic.httpStatus()).isEqualTo(429);
        assertThat(diagnostic.providerErrorCode()).isEqualTo("rate_limit_exceeded");
        assertThat(diagnostic.toString()).doesNotContain("SECRET_KEY", "PRIVATE_CHUNK_CONTENT");
    }

    @Test void unknownProviderCodeAndResponseBodyAreDiscarded() {
        var providerError = ErrorObject.builder().message("SECRET_KEY")
                .type("invalid_request_error").code("PRIVATE_CHUNK_CONTENT")
                .param(java.util.Optional.empty()).build();
        var failure = BadRequestException.builder().headers(Headers.builder().build())
                .error(providerError).build();
        var diagnostic = EmbeddingFailureDiagnostics.classify(failure, "openai");
        assertThat(diagnostic.category()).isEqualTo(Category.HTTP_ERROR);
        assertThat(diagnostic.httpStatus()).isEqualTo(400);
        assertThat(diagnostic.providerErrorCode()).isNull();
        var springError = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "PRIVATE_CHUNK_CONTENT",
                new HttpHeaders(), "SECRET_KEY".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(EmbeddingFailureDiagnostics.classify(springError, "ollama").httpStatus()).isEqualTo(400);
    }

    @Test void transportTimeoutAndUnknownHaveFixedCategories() {
        assertThat(EmbeddingFailureDiagnostics.classify(
                new OpenAIIoException("SECRET_KEY"), "openai").category()).isEqualTo(Category.TRANSPORT_ERROR);
        assertThat(EmbeddingFailureDiagnostics.classify(
                new ResourceAccessException("PRIVATE_CHUNK_CONTENT"), "ollama").category())
                .isEqualTo(Category.TRANSPORT_ERROR);
        assertThat(EmbeddingFailureDiagnostics.classify(
                new IllegalStateException(new SocketTimeoutException("SECRET_KEY")), "openai").category())
                .isEqualTo(Category.TIMEOUT);
        var unknown = EmbeddingFailureDiagnostics.classify(
                new IllegalStateException("SECRET_KEY"), "openai");
        assertThat(unknown.category()).isEqualTo(Category.UNKNOWN);
        assertThat(unknown.httpStatus()).isNull();
        assertThat(unknown.providerErrorCode()).isNull();
    }

    @Test void gatewayLogsOnlyClassifiedFieldsAndKeepsSafeExternalMessages() {
        Logger logger = (Logger) LoggerFactory.getLogger(SpringAiKnowledgeEmbeddingGateway.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var providerError = ErrorObject.builder().message("PRIVATE_CHUNK_CONTENT SECRET_KEY")
                    .type("invalid_request_error").code("invalid_api_key")
                    .param(java.util.Optional.empty()).build();
            var failure = BadRequestException.builder().headers(Headers.builder()
                    .put("Authorization", "SECRET_KEY").build()).error(providerError).build();
            when(model.call(any(EmbeddingRequest.class))).thenThrow(failure);
            var gateway = new OpenAiKnowledgeEmbeddingGateway(model,
                    new EmbeddingProfile("openai", "test-model", 768), System::nanoTime);
            assertThatThrownBy(() -> gateway.embed(request))
                    .isInstanceOf(KnowledgeEmbeddingException.class)
                    .hasMessage("Embedding provider unavailable")
                    .satisfies(thrown -> {
                        var embedding = (KnowledgeEmbeddingException) thrown;
                        assertThat(embedding.diagnostic().httpStatus()).isEqualTo(400);
                        assertThat(embedding.diagnostic().providerErrorCode()).isEqualTo("invalid_api_key");
                        assertThat(embedding.getCause()).isNull();
                    });
            assertThat(appender.list).hasSize(1);
            var event = appender.list.getFirst();
            assertThat(event.getFormattedMessage()).contains("httpStatus=400", "providerErrorCode=invalid_api_key")
                    .doesNotContain("SECRET_KEY", "PRIVATE_CHUNK_CONTENT");
            assertThat(event.getThrowableProxy()).isNull();
            when(model.call(any(EmbeddingRequest.class))).thenThrow(
                    new IllegalStateException(new SocketTimeoutException("SECRET_KEY")));
            assertThatThrownBy(() -> gateway.embed(request))
                    .isInstanceOf(KnowledgeEmbeddingTimeoutException.class)
                    .hasMessage("Embedding provider timed out");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
