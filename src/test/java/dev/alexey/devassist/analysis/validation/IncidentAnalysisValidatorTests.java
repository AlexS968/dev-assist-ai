package dev.alexey.devassist.analysis.validation;

import dev.alexey.devassist.analysis.enums.Likelihood;
import dev.alexey.devassist.analysis.exception.IncidentAnalysisValidationException;
import dev.alexey.devassist.analysis.model.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class IncidentAnalysisValidatorTests {
	private final IncidentAnalysisValidator validator = new IncidentAnalysisValidator();

	private static ProbableCause cause() {
		return new ProbableCause("Connection exhaustion", "Connections may be held too long", Likelihood.MEDIUM,
				List.of("Check active connections"));
	}

	private static InvestigationStep step(int order) {
		return new InvestigationStep(order, "Inspect metrics", "Test the hypothesis");
	}

	private static StructuredIncidentAnalysis analysis(String summary, List<ProbableCause> causes,
			List<InvestigationStep> steps, List<String> uncertainties) {
		return new StructuredIncidentAnalysis(summary, causes, steps, uncertainties);
	}

	private static StructuredIncidentAnalysis valid() {
		return analysis("Database timeouts", List.of(cause()), List.of(step(1)), List.of("Metrics are unavailable"));
	}

	@Test
	void acceptsValidObject() {
		var candidate = valid();
		assertThatCode(() -> validator.validate(candidate)).doesNotThrowAnyException();
	}

	@Test
	void acceptsAllUpperBoundsAndLikelihoodValues() {
		var causes = Arrays.stream(Likelihood.values()).map(l -> new ProbableCause("t".repeat(120),
				"e".repeat(500), l, Collections.nCopies(3, "x".repeat(250)))).toList();
		var steps = IntStream.rangeClosed(1, 4)
				.mapToObj(i -> new InvestigationStep(i, "a".repeat(300), "r".repeat(300))).toList();
		validator.validate(analysis("s".repeat(500), causes, steps, Collections.nCopies(3, "u".repeat(250))));
	}

	@Test
	void copiesAndProtectsEveryCollection() {
		var evidence = new ArrayList<>(List.of("Evidence"));
		var causes = new ArrayList<>(List.of(new ProbableCause("Title", "Explanation", Likelihood.HIGH, evidence)));
		var steps = new ArrayList<>(List.of(step(1)));
		var uncertainties = new ArrayList<>(List.of("Unknown"));
		var candidate = analysis("Summary", causes, steps, uncertainties);
		evidence.clear();
		causes.clear();
		steps.clear();
		uncertainties.clear();
		validator.validate(candidate);
		var probableCauses = candidate.probableCauses();
		var investigationSteps = candidate.investigationSteps();
		var copiedUncertainties = candidate.uncertainties();
		var evidenceToCheck = probableCauses.getFirst().evidenceToCheck();
		assertThat(evidenceToCheck).containsExactly("Evidence");
		assertThatThrownBy(probableCauses::clear).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(investigationSteps::clear).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(copiedUncertainties::clear).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(evidenceToCheck::clear).isInstanceOf(UnsupportedOperationException.class);
	}

	static Stream<String> invalidSummary() {
		return Stream.of(null, "", " \n\t", "s".repeat(501));
	}

	@ParameterizedTest
	@MethodSource("invalidSummary")
	void rejectsInvalidSummary(String summary) {
		assertViolation(analysis(summary, valid().probableCauses(), valid().investigationSteps(), valid().uncertainties()), "summary:");
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 4})
	void rejectsCauseCounts(int count) {
		assertViolation(analysis("s", Collections.nCopies(count, cause()), List.of(step(1)), List.of("u")), "probableCauses:");
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 5})
	void rejectsStepCounts(int count) {
		assertViolation(analysis("s", List.of(cause()), IntStream.rangeClosed(1, count).mapToObj(IncidentAnalysisValidatorTests::step).toList(),
				List.of("u")), "investigationSteps:");
	}

	static Stream<List<String>> invalidStrings() {
		return Stream.of(null, List.of(), List.of(""), List.of(" \n"), List.of("x".repeat(251)),
				Collections.nCopies(4, "x"), Arrays.asList((String) null));
	}

	@ParameterizedTest
	@MethodSource("invalidStrings")
	void rejectsEvidence(List<String> evidence) {
		assertViolation(analysis("s", List.of(new ProbableCause("t", "e", Likelihood.LOW, evidence)),
				List.of(step(1)), List.of("u")), "probableCauses[0].evidenceToCheck");
	}

	@ParameterizedTest
	@MethodSource("invalidStrings")
	void rejectsUncertainties(List<String> uncertainties) {
		assertViolation(analysis("s", List.of(cause()), List.of(step(1)), uncertainties), "uncertainties");
	}

	static Stream<List<InvestigationStep>> invalidOrders() {
		return Stream.of(List.of(step(1), step(1)), List.of(step(0)), List.of(step(-1)),
				List.of(step(1), step(3)), List.of(step(2), step(1)));
	}

	@ParameterizedTest
	@MethodSource("invalidOrders")
	void rejectsInvalidOrders(List<InvestigationStep> steps) {
		assertViolation(analysis("s", List.of(cause()), steps, List.of("u")), ".order:");
	}

	@Test
	void rejectsNullRootCollectionsAndElements() {
		assertViolation(null, "analysis: must not be null");
		assertThat(failure(analysis("s", null, null, null)).violations()).containsExactly(
				"investigationSteps: must not be null", "probableCauses: must not be null", "uncertainties: must not be null");
		assertThat(failure(analysis("s", Arrays.asList((ProbableCause) null),
				Arrays.asList((InvestigationStep) null), List.of("u"))).violations()).containsExactly(
				"investigationSteps[0]: must not be null", "probableCauses[0]: must not be null");
	}

	@Test
	void validatesAllNestedTextAndLikelihood() {
		for (String text : Arrays.asList(null, " \t", "x".repeat(501))) {
			var violations = failure(analysis("s", List.of(new ProbableCause(text, text, null, List.of("e"))),
					List.of(new InvestigationStep(1, text, text)), List.of("u"))).violations();
			assertThat(violations).hasSize(5).anyMatch(v -> v.startsWith("probableCauses[0].title:"))
					.anyMatch(v -> v.startsWith("probableCauses[0].explanation:"))
					.anyMatch(v -> v.startsWith("probableCauses[0].likelihood:"))
					.anyMatch(v -> v.startsWith("investigationSteps[0].action:"))
					.anyMatch(v -> v.startsWith("investigationSteps[0].rationale:"));
		}
		assertViolation(analysis("s", List.of(new ProbableCause("x".repeat(121), "e", Likelihood.LOW, List.of("e"))),
				List.of(step(1)), List.of("u")), ".title:");
		assertThat(failure(analysis("s", List.of(cause()), List.of(new InvestigationStep(1,
				"a".repeat(301), "r".repeat(301))), List.of("u"))).violations()).hasSize(2);
	}

	@Test
	void aggregatesViolationsInDeterministicOrderWithoutSensitiveData() {
		String secret = "PRIVATE incident description prompt model response ".repeat(20);
		var candidate = analysis(secret, List.of(new ProbableCause(secret, secret, null, List.of(secret))),
				List.of(new InvestigationStep(0, secret, secret), step(0)), List.of(secret));
		var exception = failure(candidate);
		assertThat(exception.violations()).hasSize(11).isSorted();
		for (int i = 0; i < 10; i++) {
			assertThat(failure(candidate).violations()).containsExactlyElementsOf(exception.violations());
		}
		assertThat(exception.getCause()).isNull();
		var trace = new StringWriter();
		exception.printStackTrace(new PrintWriter(trace));
		assertThat(trace.toString() + exception.violations()).doesNotContain("PRIVATE", secret);
		var violations = exception.violations();
		assertThatThrownBy(violations::clear).isInstanceOf(UnsupportedOperationException.class);
	}

	private IncidentAnalysisValidationException failure(StructuredIncidentAnalysis candidate) {
		return catchThrowableOfType(IncidentAnalysisValidationException.class, () -> validator.validate(candidate));
	}

	private void assertViolation(StructuredIncidentAnalysis candidate, String path) {
		assertThat(failure(candidate).violations()).anyMatch(v -> v.contains(path));
	}
}
