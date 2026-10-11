package dev.alexey.devassist.ragevaluation;

import dev.alexey.devassist.analysis.grounding.IncidentGroundingStatus;
import dev.alexey.devassist.incident.dto.IncidentAnalysisResponseDTO;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class LiveRagSmokeReportTests {
    @Test void noRelevantContextSerializesOnlySafeObservedFields() {
        var response = new IncidentAnalysisResponseDTO(null, "openai", "gpt-6-luna",
                "incident-analysis-v3", Instant.EPOCH, 12, 7, 8, 15, 1,
                IncidentGroundingStatus.NO_RELEVANT_CONTEXT, false, UUID.randomUUID(),
                List.of(), 4, 3, 0, 0, 0);
        String json = JsonMapper.shared().writeValueAsString(LiveRagSmokeReport.snapshot(response, 0.70));
        assertThat(json).contains("\"responseStatus\":\"ANALYSIS_RETURNED\"",
                "\"groundingStatus\":\"NO_RELEVANT_CONTEXT\"",
                "\"grounded\":false",
                "\"retrievedChunkCount\":0",
                "\"providedContextChunkCount\":0",
                "\"citationCount\":0",
                "\"similarityThreshold\":0.7",
                "Expected context not found");
        assertThat(json).doesNotContain("gpt-6-luna", "SECRET_KEY", "INCIDENT_CONTENT",
                "inputTokens", "outputTokens", "prompt", "citations");
    }
}
