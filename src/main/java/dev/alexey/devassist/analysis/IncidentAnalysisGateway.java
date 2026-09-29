package dev.alexey.devassist.analysis;

/** Application boundary for generating validated structured analysis of an incident. */
public interface IncidentAnalysisGateway {

	IncidentAnalysisResult analyze(IncidentAnalysisInput incident);
}
