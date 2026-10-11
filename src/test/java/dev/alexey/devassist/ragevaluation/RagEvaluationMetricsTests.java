package dev.alexey.devassist.ragevaluation;

import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import dev.alexey.devassist.knowledge.retrieval.RetrievedKnowledgeChunk;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RagEvaluationMetricsTests {
    @Test void metricsUseDistinctExpectedDocumentsAndFirstRelevantRank() {
        var scenario = new RagEvaluationScenario("metrics", "payment-service", "Query", "Description",
                Set.of("a", "b"), Set.of("forbidden"), 1, "metric test");
        var ranked = List.of(chunk("unrelated", 0.95), chunk("a", 0.8), chunk("a", 0.79),
                chunk("b", 0.7));
        var metrics = RagEvaluationMetrics.evaluate(scenario, ranked, 4);
        assertThat(metrics.hitAt1()).isFalse();
        assertThat(metrics.hitAt3()).isTrue();
        assertThat(metrics.recallAtK()).isEqualTo(1);
        assertThat(metrics.reciprocalRank()).isEqualTo(0.5);
        assertThat(metrics.relevantHits()).isEqualTo(2);
    }

    @Test void sweepFiltersScoresLocallyWithoutProviderCalls() {
        var scenario = new RagEvaluationScenario("metrics", "payment-service", "Query", "Description",
                Set.of("a"), Set.of("forbidden"), 1, "metric test");
        var results = List.of(new RagEvaluationMetrics.ScenarioResult(scenario,
                List.of(chunk("a", 0.72), chunk("forbidden", 0.55))));
        var sweep = RagEvaluationMetrics.sweep(results, 5);
        assertThat(sweep).hasSize(8);
        assertThat(sweep.get(0).relevantHits()).isEqualTo(1);
        assertThat(sweep.get(0).forbiddenHits()).isEqualTo(1);
        assertThat(sweep.get(4).threshold()).isEqualTo(0.70);
        assertThat(sweep.get(4).forbiddenHits()).isZero();
        assertThat(sweep.get(6).emptyScenarios()).isEqualTo(1);
    }

    private static RetrievedKnowledgeChunk chunk(String externalId, double score) {
        return new RetrievedKnowledgeChunk(UUID.randomUUID(), UUID.randomUUID(), externalId, externalId,
                KnowledgeSourceType.RUNBOOK, null, 0, "content", Map.of(), score);
    }
}
