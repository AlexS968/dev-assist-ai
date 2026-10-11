package dev.alexey.devassist.ragevaluation;

import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import dev.alexey.devassist.knowledge.retrieval.KnowledgeRetrievalResult;
import dev.alexey.devassist.knowledge.retrieval.RetrievedKnowledgeChunk;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class LiveRagSmokeRetrievalProbeTests {
    @TempDir Path temporaryDirectory;

    @Test void probeReportsScoresThresholdAndContextEligibilityWithoutContent() {
        var result = result(List.of(chunk("runbook", "SECRET_CHUNK", 0.72),
                chunk("architecture", "SECOND_CHUNK", 0.68)));
        var snapshot = LiveRagSmokeRetrievalProbe.snapshot(result, 0.70, 5, 5, 6000);
        String json = JsonMapper.shared().writeValueAsString(snapshot);
        assertThat(json).contains("\"similarityThreshold\":0.7", "\"externalId\":\"runbook\"",
                "\"externalId\":\"architecture\"", "\"similarity\":0.72",
                "\"similarity\":0.68", "\"matchesAboveThreshold\":1",
                "\"contextEligibleChunkCount\":1")
                .doesNotContain("SECRET_CHUNK", "SECOND_CHUNK", "SECRET_TITLE",
                "SECRET_URI", "SECRET_METADATA", "\"query\":", "prompt", "vector");
    }

    @Test void failedSmokeKeepsProbeAndObservedStatusBeforeRethrowing() throws Exception {
        var report = new LinkedHashMap<String, Object>();
        report.put("stage", "grounded_smoke");
        report.put("groundedSmokeRetrievalProbe", LiveRagSmokeRetrievalProbe.snapshot(
                result(List.of(chunk("runbook", "SECRET_CHUNK", 0.62))), 0.70, 5, 5, 6000));
        report.put("groundedSmoke", Map.of("groundingStatus", "NO_RELEVANT_CONTEXT",
                "grounded", false, "retrievedChunkCount", 0, "providedContextChunkCount", 0,
                "citationCount", 0));
        AssertionError failure = new AssertionError("SECRET_KEY INCIDENT_CONTENT");
        Path output = temporaryDirectory.resolve("openai-report.json");
        assertThatThrownBy(() -> LiveRagEvaluationRunner.run("openai", report, () -> {
            throw failure;
        }, (mode, fields) -> JsonMapper.shared().writeValue(output.toFile(), fields)))
                .isSameAs(failure);
        String json = Files.readString(output);
        assertThat(json).contains("\"status\":\"FAILED\"", "NO_RELEVANT_CONTEXT",
                "\"externalId\":\"runbook\"", "\"similarity\":0.62",
                "\"similarityThreshold\":0.7", "\"matchesAboveThreshold\":0")
                .doesNotContain("SECRET_KEY", "INCIDENT_CONTENT", "SECRET_CHUNK",
                "stackTrace", "prompt");
    }

    private KnowledgeRetrievalResult result(List<RetrievedKnowledgeChunk> matches) {
        return new KnowledgeRetrievalResult(UUID.randomUUID(),
                new EmbeddingProfile("openai", "text-embedding-3-small", 768),
                matches, 3, 12, 4, 20);
    }

    private RetrievedKnowledgeChunk chunk(String externalId, String content, double similarity) {
        return new RetrievedKnowledgeChunk(UUID.randomUUID(), UUID.randomUUID(), externalId,
                "SECRET_TITLE", KnowledgeSourceType.RUNBOOK, "SECRET_URI", 0,
                content, Map.of("secret", "SECRET_METADATA"), similarity);
    }
}
