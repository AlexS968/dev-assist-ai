package dev.alexey.devassist.analysis;

/** Application boundary for generating plain-text analysis of an incident. */
public interface IncidentAnalysisGateway {

	IncidentAnalysisResult analyze(IncidentAnalysisInput incident);
}
