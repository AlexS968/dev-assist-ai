package dev.alexey.devassist.analysis.validation;

import dev.alexey.devassist.analysis.exception.IncidentAnalysisValidationException;
import dev.alexey.devassist.analysis.model.StructuredIncidentAnalysis;
import dev.alexey.devassist.analysis.model.ProbableCause;
import dev.alexey.devassist.analysis.model.InvestigationStep;
import dev.alexey.devassist.analysis.grounding.IncidentAnalysisContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Explicit, stateless validation boundary for the complete candidate graph. */
public final class IncidentAnalysisValidator {

	public void validate(StructuredIncidentAnalysis analysis) {
		List<String> errors = new ArrayList<>();
		validateAnalysis(analysis, errors);
		if (!errors.isEmpty()) {
			throw new IncidentAnalysisValidationException(errors);
		}
	}

	private static void validateAnalysis(StructuredIncidentAnalysis analysis, List<String> errors) {
		if (analysis == null) {
			errors.add("analysis: must not be null");
			return;
		}
		text(analysis.summary(), "summary", 500, errors);
		validateProbableCauses(analysis.probableCauses(), errors);
		validateInvestigationSteps(analysis.investigationSteps(), errors);
		strings(analysis.uncertainties(), "uncertainties", errors);
		validateCitationIds(analysis.citationIds(), errors);
	}

	/** Context-aware semantic validation after provider conversion. */
	public void validateCitations(StructuredIncidentAnalysis analysis, IncidentAnalysisContext context) {
		validate(analysis);
		var allowed = new HashSet<String>();
		context.sources().forEach(source -> allowed.add(source.sourceId()));
		for (String id : analysis.citationIds()) {
			if (!allowed.contains(id)) {
				throw new IncidentAnalysisValidationException(List.of("citationIds: unknown source ID"));
			}
		}
	}

	private static void validateCitationIds(List<String> ids, List<String> errors) {
		if (ids == null) {
			errors.add("citationIds: must not be null");
			return;
		}
		var seen = new HashSet<String>();
		for (String id : ids) {
			if (id == null || id.isBlank()) errors.add("citationIds: must contain nonblank IDs");
			else if (!seen.add(id)) errors.add("citationIds: must be unique");
		}
	}

	private static void validateProbableCauses(List<ProbableCause> causes, List<String> errors) {
		if (!collection(causes, "probableCauses", 3, errors)) {
			return;
		}
		for (int i = 0; i < causes.size(); i++) {
			validateProbableCause(causes.get(i), "probableCauses[" + i + "]", errors);
		}
	}

	private static void validateProbableCause(ProbableCause cause, String path, List<String> errors) {
		if (cause == null) {
			errors.add(path + ": must not be null");
			return;
		}
		text(cause.title(), path + ".title", 120, errors);
		text(cause.explanation(), path + ".explanation", 500, errors);
		if (cause.likelihood() == null) {
			errors.add(path + ".likelihood: must not be null");
		}
		strings(cause.evidenceToCheck(), path + ".evidenceToCheck", errors);
	}

	private static void validateInvestigationSteps(List<InvestigationStep> steps, List<String> errors) {
		if (!collection(steps, "investigationSteps", 4, errors)) {
			return;
		}
		var orders = new HashSet<Integer>();
		for (int i = 0; i < steps.size(); i++) {
			validateInvestigationStep(steps.get(i), i, orders, errors);
		}
	}

	private static void validateInvestigationStep(InvestigationStep step, int index,
			Set<Integer> orders, List<String> errors) {
		String path = "investigationSteps[" + index + "]";
		if (step == null) {
			errors.add(path + ": must not be null");
			return;
		}
		text(step.action(), path + ".action", 300, errors);
		text(step.rationale(), path + ".rationale", 300, errors);
		if (!orders.add(step.order())) {
			errors.add(path + ".order: must be unique");
		}
		if (step.order() != index + 1) {
			errors.add(path + ".order: must equal its one-based list position (1..N)");
		}
	}

	private static void text(String value, String path, int max, List<String> errors) {
		if (value == null || value.isBlank()) {
			errors.add(path + ": must not be blank");
		}
		if (value != null && value.length() > max) {
			errors.add(path + ": must contain at most " + max + " characters");
		}
	}

	private static boolean collection(List<?> values, String path, int max, List<String> errors) {
		if (values == null) {
			errors.add(path + ": must not be null");
			return false;
		}
		if (values.isEmpty() || values.size() > max) {
			errors.add(path + ": must contain between 1 and " + max + " elements");
		}
		return true;
	}

	private static void strings(List<String> values, String path, List<String> errors) {
		if (collection(values, path, 3, errors)) {
			for (int i = 0; i < values.size(); i++) {
				text(values.get(i), path + "[" + i + "]", 250, errors);
			}
		}
	}
}
