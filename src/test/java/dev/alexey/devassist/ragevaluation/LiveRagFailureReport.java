package dev.alexey.devassist.ragevaluation;

import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingDiagnostic;
import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Failure reports contain only classified fields; never serialize Throwable. */
final class LiveRagFailureReport {
    private LiveRagFailureReport() {}

    static Map<String, Object> fields(Throwable failure, String provider, String stage) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("provider", provider);
        fields.put("stage", stage);
        KnowledgeEmbeddingDiagnostic diagnostic = failure instanceof KnowledgeEmbeddingException embedding
                ? embedding.diagnostic() : null;
        String category = diagnostic != null ? diagnostic.category().name()
                : failure instanceof AssertionError ? "ASSERTION_FAILURE" : "UNCLASSIFIED_FAILURE";
        fields.put("category", category);
        fields.put("httpStatus", diagnostic == null ? null : diagnostic.httpStatus());
        fields.put("providerErrorCode", diagnostic == null ? null : diagnostic.providerErrorCode());
        return fields;
    }
}
