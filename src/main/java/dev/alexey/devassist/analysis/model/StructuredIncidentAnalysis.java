package dev.alexey.devassist.analysis.model;

import java.util.List;

/** Untrusted analysis candidate; validate before use. Hypotheses are not established root causes. */
public record StructuredIncidentAnalysis(String summary, List<ProbableCause> probableCauses,
		List<InvestigationStep> investigationSteps, List<String> uncertainties, List<String> citationIds) {

    public StructuredIncidentAnalysis(String summary, List<ProbableCause> probableCauses,
            List<InvestigationStep> investigationSteps, List<String> uncertainties) {
        this(summary, probableCauses, investigationSteps, uncertainties, List.of());
    }

	public StructuredIncidentAnalysis {
		probableCauses = ImmutableLists.copy(probableCauses);
		investigationSteps = ImmutableLists.copy(investigationSteps);
		uncertainties = ImmutableLists.copy(uncertainties);
        citationIds = ImmutableLists.copy(citationIds);
	}
}
