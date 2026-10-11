package dev.alexey.devassist.analysis.grounding;

import dev.alexey.devassist.knowledge.retrieval.RetrievedKnowledgeChunk;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public final class IncidentAnalysisContextSelector {
    public IncidentAnalysisContext select(List<RetrievedKnowledgeChunk> retrieved, int maxChunks, int maxChars) {
        List<IncidentAnalysisSource> selected = new ArrayList<>();
        var seen = new HashSet<UUID>();
        int used = 0;
        for (RetrievedKnowledgeChunk chunk : retrieved) {
            if (selected.size() == maxChunks) break;
            if (seen.add(chunk.chunkId()) && chunk.content().length() <= maxChars - used) {
                selected.add(new IncidentAnalysisSource("S" + (selected.size() + 1), chunk));
                used += chunk.content().length();
            }
        }
        return new IncidentAnalysisContext(selected, used);
    }
}
