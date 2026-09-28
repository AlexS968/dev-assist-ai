package dev.alexey.devassist.analysis.model;

import java.util.List;

/** Untrusted analysis candidate; validate before use. Hypotheses are not established root causes. */
public record StructuredIncidentAnalysis(String summary, List<ProbableCause> probableCauses,
		List<InvestigationStep> investigationSteps, List<String> uncertainties) {

	public StructuredIncidentAnalysis {
		probableCauses = ImmutableLists.copy(probableCauses);
		investigationSteps = ImmutableLists.copy(investigationSteps);
		uncertainties = ImmutableLists.copy(uncertainties);
	}
}
