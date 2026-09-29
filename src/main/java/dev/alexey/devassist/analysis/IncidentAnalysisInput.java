package dev.alexey.devassist.analysis;

/** Immutable incident snapshot, independent of persistence and AI providers. */
public record IncidentAnalysisInput(String title, String description, boolean repair) {
	public IncidentAnalysisInput(String title, String description) {
		this(title, description, false);
	}
}
