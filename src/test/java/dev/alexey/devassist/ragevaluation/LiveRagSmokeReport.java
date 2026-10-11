package dev.alexey.devassist.ragevaluation;

import dev.alexey.devassist.analysis.grounding.IncidentGroundingStatus;
import dev.alexey.devassist.incident.dto.IncidentAnalysisResponseDTO;
import java.util.LinkedHashMap;
import java.util.Map;

/** Safe snapshot made before grounded smoke assertions can fail. */
final class LiveRagSmokeReport {
    private LiveRagSmokeReport() {}

    static Map<String, Object> snapshot(IncidentAnalysisResponseDTO response, double threshold) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("responseStatus", "ANALYSIS_RETURNED");
        fields.put("groundingStatus", response.groundingStatus());
        fields.put("grounded", response.grounded());
        fields.put("retrievedChunkCount", response.retrievedChunkCount());
        fields.put("providedContextChunkCount", response.providedContextChunkCount());
        fields.put("citationCount", response.citations().size());
        if (response.groundingStatus() == IncidentGroundingStatus.NO_RELEVANT_CONTEXT) {
            fields.put("qualityObservation", "Expected context not found at configured similarity threshold");
            fields.put("similarityThreshold", threshold);
        }
        return fields;
    }
}
