package dev.alexey.devassist.analysis;

import dev.alexey.devassist.analysis.enums.Likelihood;
import dev.alexey.devassist.analysis.model.InvestigationStep;
import dev.alexey.devassist.analysis.model.ProbableCause;
import dev.alexey.devassist.analysis.model.StructuredIncidentAnalysis;
import java.util.List;

public final class StructuredAnalysisFixtures {
	private StructuredAnalysisFixtures() {
	}

	public static final String JSON = """
			{
			  "summary": "Database timeouts",
			  "probableCauses": [{
			    "title": "Connection exhaustion",
			    "explanation": "Connections may be held too long",
			    "likelihood": "MEDIUM",
			    "evidenceToCheck": ["Check active connections"]
			  }],
			  "investigationSteps": [{
			    "order": 1,
			    "action": "Inspect metrics",
			    "rationale": "Test the hypothesis"
			  }],
			  "uncertainties": ["Metrics are unavailable"]
			}
			""";

	public static StructuredIncidentAnalysis analysis() {
		return new StructuredIncidentAnalysis("Database timeouts",
				List.of(new ProbableCause("Connection exhaustion", "Connections may be held too long",
						Likelihood.MEDIUM, List.of("Check active connections"))),
				List.of(new InvestigationStep(1, "Inspect metrics", "Test the hypothesis")),
				List.of("Metrics are unavailable"));
	}
}
