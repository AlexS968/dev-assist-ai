package dev.alexey.devassist.analysis.grounding;

import java.util.List;

public record IncidentAnalysisContext(List<IncidentAnalysisSource> sources, int characterCount) {
    public static final IncidentAnalysisContext EMPTY = new IncidentAnalysisContext(List.of(), 0);

    public IncidentAnalysisContext {
        sources = List.copyOf(sources);
    }
}
