package dev.alexey.devassist.analysis;

import dev.alexey.devassist.analysis.grounding.IncidentAnalysisContext;

/** Immutable incident snapshot, independent of persistence and AI providers. */
public record IncidentAnalysisInput(String title, String description, boolean repair,
        IncidentAnalysisContext context) {
    public IncidentAnalysisInput(String title, String description) {
        this(title, description, false, IncidentAnalysisContext.EMPTY);
    }
    public IncidentAnalysisInput(String title, String description, boolean repair) {
        this(title, description, repair, IncidentAnalysisContext.EMPTY);
    }
    public IncidentAnalysisInput {
        if (context == null) throw new IllegalArgumentException("Analysis context is required");
    }
}
