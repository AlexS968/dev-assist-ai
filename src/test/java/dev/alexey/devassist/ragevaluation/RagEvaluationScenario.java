package dev.alexey.devassist.ragevaluation;

import java.util.Set;

record RagEvaluationScenario(String scenarioId, String knowledgeBaseKey, String queryTitle,
        String queryDescription, Set<String> expectedRelevantExternalIds,
        Set<String> forbiddenExternalIds, int expectedMinimumRelevantResults, String notes) {
    RagEvaluationScenario {
        expectedRelevantExternalIds = Set.copyOf(expectedRelevantExternalIds);
        forbiddenExternalIds = Set.copyOf(forbiddenExternalIds);
        if (scenarioId.isBlank() || knowledgeBaseKey.isBlank() || queryTitle.isBlank()
                || expectedMinimumRelevantResults < 0
                || !java.util.Collections.disjoint(expectedRelevantExternalIds, forbiddenExternalIds)) {
            throw new IllegalArgumentException("Invalid evaluation scenario");
        }
    }

    String query() {
        return queryTitle + "\n" + queryDescription;
    }
}
