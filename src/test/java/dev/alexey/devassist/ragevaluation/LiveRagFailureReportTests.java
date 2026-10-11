package dev.alexey.devassist.ragevaluation;

import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingDiagnostic;
import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class LiveRagFailureReportTests {
    @TempDir Path temporaryDirectory;

    @Test void embeddingFailureReportContainsOnlyClassifiedFields() {
        var diagnostic = new KnowledgeEmbeddingDiagnostic("openai",
                KnowledgeEmbeddingDiagnostic.Category.HTTP_ERROR, 429, "rate_limit_exceeded");
        var failure = new KnowledgeEmbeddingException("Embedding provider unavailable", diagnostic);
        var fields = LiveRagFailureReport.fields(failure, "openai", "embedding");
        String json = JsonMapper.shared().writeValueAsString(fields);
        assertThat(json).contains("\"provider\":\"openai\"", "\"stage\":\"embedding\"",
                "\"httpStatus\":429", "\"providerErrorCode\":\"rate_limit_exceeded\"");
        assertThat(json).doesNotContain("stackTrace", "cause", "message", "PRIVATE_CHUNK_CONTENT", "SECRET_KEY");
    }

    @Test void unclassifiedFailureDoesNotSerializeThrowable() {
        var fields = LiveRagFailureReport.fields(new IllegalStateException(
                "SECRET_KEY PRIVATE_CHUNK_CONTENT"), "ollama", "embedding");
        String json = JsonMapper.shared().writeValueAsString(fields);
        assertThat(json).contains("UNCLASSIFIED_FAILURE");
        assertThat(json).doesNotContain("SECRET_KEY", "PRIVATE_CHUNK_CONTENT", "IllegalStateException");
    }

    @Test void runnerWritesFailureReportBeforeRethrowing() throws Exception {
        var diagnostic = new KnowledgeEmbeddingDiagnostic("openai",
                KnowledgeEmbeddingDiagnostic.Category.HTTP_ERROR, 429, "rate_limit_exceeded");
        var failure = new KnowledgeEmbeddingException("Embedding provider unavailable", diagnostic);
        var report = new LinkedHashMap<String, Object>();
        report.put("stage", "embedding");
        Path output = temporaryDirectory.resolve("openai-report.json");
        assertThatThrownBy(() -> LiveRagEvaluationRunner.run("openai", report, () -> {
            throw failure;
        }, (mode, fields) -> JsonMapper.shared().writeValue(output.toFile(), fields)))
                .isSameAs(failure);
        assertThat(Files.exists(output)).isTrue();
        String json = Files.readString(output);
        assertThat(json).contains("\"status\":\"FAILED\"", "\"stage\":\"embedding\"",
                "\"httpStatus\":429");
        assertThat(json).doesNotContain("stackTrace", "SECRET_KEY", "PRIVATE_CHUNK_CONTENT");
    }

    @Test void assertionErrorWritesSafeReportAndRethrowsOriginal() throws Exception {
        var failure = new AssertionError("SECRET_KEY INCIDENT_CONTENT full model response");
        var report = new LinkedHashMap<String, Object>();
        report.put("stage", "grounded_smoke");
        report.put("groundedSmoke", java.util.Map.of(
                "responseStatus", "ANALYSIS_RETURNED",
                "groundingStatus", "NO_RELEVANT_CONTEXT",
                "grounded", false,
                "retrievedChunkCount", 0,
                "providedContextChunkCount", 0,
                "citationCount", 0));
        Path output = temporaryDirectory.resolve("assertion-report.json");
        assertThatThrownBy(() -> LiveRagEvaluationRunner.run("openai", report, () -> {
            throw failure;
        }, (mode, fields) -> JsonMapper.shared().writeValue(output.toFile(), fields)))
                .isSameAs(failure);
        String json = Files.readString(output);
        assertThat(json).contains("\"status\":\"FAILED\"", "\"category\":\"ASSERTION_FAILURE\"",
                "\"groundingStatus\":\"NO_RELEVANT_CONTEXT\"", "\"citationCount\":0");
        assertThat(json).doesNotContain("SECRET_KEY", "INCIDENT_CONTENT", "full model response",
                "stackTrace", "prompt", "embedding");
    }

    @Test void originalAssertionSurvivesReportWriteFailure() {
        var failure = new AssertionError("original assertion");
        var report = new LinkedHashMap<String, Object>();
        assertThatThrownBy(() -> LiveRagEvaluationRunner.run("openai", report, () -> {
            throw failure;
        }, (mode, fields) -> {
            throw new java.io.IOException("report write failed");
        })).isSameAs(failure);
        assertThat(failure.getSuppressed()).hasSize(1);
    }
}
