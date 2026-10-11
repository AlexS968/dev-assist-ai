package dev.alexey.devassist.knowledge.embedding.infrastructure;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIServiceException;
import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingDiagnostic;
import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingDiagnostic.Category;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

final class EmbeddingFailureDiagnostics {
    private static final Set<String> SAFE_CODES = Set.of(
            "invalid_api_key", "insufficient_quota", "rate_limit_exceeded",
            "model_not_found", "invalid_request_error");

    private EmbeddingFailureDiagnostics() {}

    static KnowledgeEmbeddingDiagnostic classify(Throwable failure, String provider) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable cause = failure;
        while (cause != null && seen.add(cause)) {
            if (isTimeout(cause)) {
                return new KnowledgeEmbeddingDiagnostic(provider, Category.TIMEOUT, null, null);
            }
            cause = cause.getCause();
        }
        seen.clear();
        cause = failure;
        while (cause != null && seen.add(cause)) {
            if (cause instanceof OpenAIServiceException service) {
                return new KnowledgeEmbeddingDiagnostic(provider, Category.HTTP_ERROR,
                        validStatus(service.statusCode()), safeCode(service));
            }
            if (cause instanceof RestClientResponseException response) {
                return new KnowledgeEmbeddingDiagnostic(provider, Category.HTTP_ERROR,
                        validStatus(response.getStatusCode().value()), null);
            }
            if (cause instanceof OpenAIIoException || cause instanceof ResourceAccessException) {
                return new KnowledgeEmbeddingDiagnostic(provider, Category.TRANSPORT_ERROR, null, null);
            }
            cause = cause.getCause();
        }
        return new KnowledgeEmbeddingDiagnostic(provider, Category.UNKNOWN, null, null);
    }

    private static boolean isTimeout(Throwable cause) {
        return cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException
                || cause instanceof TimeoutException;
    }

    private static Integer validStatus(int status) {
        return status >= 100 && status <= 599 ? status : null;
    }

    private static String safeCode(OpenAIServiceException service) {
        try {
            return knownCode(service.code().orElse(null));
        } catch (OpenAIInvalidDataException invalidCode) {
            return null;
        }
    }

    private static String knownCode(String code) {
        return code != null && SAFE_CODES.contains(code) ? code : null;
    }
}
