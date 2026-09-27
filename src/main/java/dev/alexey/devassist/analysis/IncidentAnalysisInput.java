package dev.alexey.devassist.analysis;

/** Immutable incident snapshot, independent of persistence and AI providers. */
public record IncidentAnalysisInput(String title, String description) {
}
