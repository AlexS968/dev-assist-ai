package dev.alexey.devassist.analysis.model;

import dev.alexey.devassist.analysis.enums.Likelihood;
import java.util.List;

public record ProbableCause(String title, String explanation, Likelihood likelihood, List<String> evidenceToCheck) {
	public ProbableCause {
		evidenceToCheck = ImmutableLists.copy(evidenceToCheck);
	}
}
