package dev.alexey.devassist.ragevaluation;

import dev.alexey.devassist.knowledge.retrieval.RetrievedKnowledgeChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class RagEvaluationMetrics {
    static final List<Double> THRESHOLDS = List.of(0.50, 0.55, 0.60, 0.65, 0.70, 0.75, 0.80, 0.85);

    private RagEvaluationMetrics() {}

    static ScenarioMetrics evaluate(RagEvaluationScenario scenario, List<RetrievedKnowledgeChunk> ranked, int topK) {
        List<RetrievedKnowledgeChunk> selected = ranked.stream().limit(topK).toList();
        Set<String> expected = scenario.expectedRelevantExternalIds();
        long relevant = selected.stream().map(RetrievedKnowledgeChunk::externalId).distinct()
                .filter(expected::contains).count();
        long forbidden = selected.stream().filter(chunk ->
                scenario.forbiddenExternalIds().contains(chunk.externalId())).count();
        int firstRank = 0;
        for (int index = 0; index < selected.size(); index++) {
            if (expected.contains(selected.get(index).externalId())) {
                firstRank = index + 1;
                break;
            }
        }
        double recall = expected.isEmpty() ? 0.0 : (double) relevant / expected.size();
        return new ScenarioMetrics(!selected.isEmpty() && expected.contains(selected.getFirst().externalId()),
                firstRank > 0 && firstRank <= 3, recall, firstRank == 0 ? 0.0 : 1.0 / firstRank,
                selected.isEmpty(), forbidden, relevant, selected.size());
    }

    static List<ThresholdMetrics> sweep(List<ScenarioResult> results, int topK) {
        List<ThresholdMetrics> sweep = new ArrayList<>();
        for (double threshold : THRESHOLDS) {
            List<ScenarioMetrics> metrics = results.stream().map(result -> evaluate(result.scenario(),
                    result.matches().stream().filter(chunk -> chunk.similarity() >= threshold).toList(), topK))
                    .toList();
            sweep.add(new ThresholdMetrics(threshold, metrics.stream().mapToLong(ScenarioMetrics::relevantHits).sum(),
                    metrics.stream().mapToLong(ScenarioMetrics::forbiddenHits).sum(),
                    metrics.stream().filter(ScenarioMetrics::empty).count(),
                    metrics.stream().filter(ScenarioMetrics::hitAt1).count(),
                    metrics.stream().filter(ScenarioMetrics::hitAt3).count(),
                    metrics.stream().filter(metric -> metric.relevantHits() > 0).count(),
                    metrics.stream().mapToDouble(ScenarioMetrics::recallAtK).average().orElse(0)));
        }
        return List.copyOf(sweep);
    }

    record ScenarioResult(RagEvaluationScenario scenario, List<RetrievedKnowledgeChunk> matches) {
        ScenarioResult {
            matches = List.copyOf(matches);
        }
    }

    record ScenarioMetrics(boolean hitAt1, boolean hitAt3, double recallAtK, double reciprocalRank,
            boolean empty, long forbiddenHits, long relevantHits, int returnedHits) {}

    record ThresholdMetrics(double threshold, long relevantHits, long forbiddenHits,
            long emptyScenarios, long hitAt1, long hitAt3, long hitAtK, double meanRecallAtK) {}
}
