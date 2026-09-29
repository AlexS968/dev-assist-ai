package dev.alexey.devassist.analysis;

import dev.alexey.devassist.analysis.exception.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static dev.alexey.devassist.analysis.StructuredAnalysisFixtures.analysis;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentAnalysisRepairPolicyTests {
	private final IncidentAnalysisGateway gateway = mock(IncidentAnalysisGateway.class);
	private final AtomicLong ticks = new AtomicLong();
	private final IncidentAnalysisRepairPolicy policy = new IncidentAnalysisRepairPolicy(gateway, ticks::get);
	private final IncidentAnalysisInput input = new IncidentAnalysisInput("Title", "Description");
	private final IncidentAnalysisInput repair = new IncidentAnalysisInput("Title", "Description", true);

	@Test
	void firstSuccessPreservesMetricsAndDoesNotRepair() {
		var result = result(12, 8, 20);
		when(gateway.analyze(input)).thenReturn(result);
		assertThat(policy.analyze(input)).isSameAs(result);
		assertThat(result.attemptCount()).isEqualTo(1);
		verify(gateway).analyze(input);
		verifyNoMoreInteractions(gateway);
	}

	@ParameterizedTest
	@MethodSource("repairableFailures")
	void repairsOnceAndMeasuresWholeOrchestration(IncidentAnalysisException failure) {
		when(gateway.analyze(input)).thenAnswer(invocation -> {
			ticks.addAndGet(100_000_000L);
			throw failure;
		});
		when(gateway.analyze(repair)).thenAnswer(invocation -> {
			ticks.addAndGet(250_000_000L);
			return result(12, 8, 20);
		});
		var result = policy.analyze(input);
		assertThat(result.attemptCount()).isEqualTo(2);
		assertThat(result.latencyMs()).isEqualTo(350);
		assertThat(result.inputTokens()).isEqualTo(22);
		assertThat(result.outputTokens()).isEqualTo(13);
		assertThat(result.totalTokens()).isEqualTo(35);
		assertThat(result.analysis()).isEqualTo(analysis());
		assertThat(result.provider()).isEqualTo("fake");
		assertThat(result.model()).isEqualTo("model");
		assertThat(result.promptVersion()).isEqualTo("incident-analysis-v2");
		assertThat(result.generatedAt()).isEqualTo(Instant.EPOCH);
		verifyTwoCalls();
	}

	static Stream<IncidentAnalysisException> repairableFailures() {
		var usage = new IncidentAnalysisAttemptUsage(10, 5, 15);
		return Stream.of(new IncidentAnalysisConversionException(usage),
				new IncidentAnalysisValidationException(List.of("summary: must not be blank"), usage));
	}

	@ParameterizedTest
	@MethodSource("failurePairs")
	void secondFailurePropagatesWithoutThirdCall(IncidentAnalysisException first, IncidentAnalysisException second) {
		when(gateway.analyze(input)).thenThrow(first);
		when(gateway.analyze(repair)).thenThrow(second);
		assertThatThrownBy(() -> policy.analyze(input)).isSameAs(second);
		verifyTwoCalls();
	}

	static Stream<Arguments> failurePairs() {
		return repairableFailures().flatMap(first -> Stream.concat(repairableFailures(), nonRepairableFailures())
				.map(second -> Arguments.of(first, second)));
	}

	@ParameterizedTest
	@MethodSource("nonRepairableFailures")
	void doesNotRepairProviderTimeoutOrEmptyFailure(IncidentAnalysisException failure) {
		when(gateway.analyze(input)).thenThrow(failure);
		assertThatThrownBy(() -> policy.analyze(input)).isSameAs(failure);
		verify(gateway).analyze(input);
		verifyNoMoreInteractions(gateway);
	}

	static Stream<IncidentAnalysisException> nonRepairableFailures() {
		return Stream.of(new IncidentAnalysisTimeoutException(new IllegalStateException("private")),
				new IncidentAnalysisException(new IllegalStateException("private")), new EmptyIncidentAnalysisException());
	}

	@ParameterizedTest
	@MethodSource("usageCases")
	void sumsEachFieldOnlyWhenBothAttemptsReportIt(IncidentAnalysisAttemptUsage first,
			IncidentAnalysisAttemptUsage second, IncidentAnalysisAttemptUsage expected) {
		when(gateway.analyze(input)).thenThrow(new IncidentAnalysisConversionException(first));
		when(gateway.analyze(repair)).thenReturn(result(second.inputTokens(), second.outputTokens(), second.totalTokens()));
		var result = policy.analyze(input);
		assertThat(new IncidentAnalysisAttemptUsage(result.inputTokens(), result.outputTokens(), result.totalTokens()))
				.isEqualTo(expected);
		verifyTwoCalls();
	}

	static Stream<Arguments> usageCases() {
		var all = new IncidentAnalysisAttemptUsage(10, 5, 15);
		var noInput = new IncidentAnalysisAttemptUsage(null, 5, 15);
		var noOutput = new IncidentAnalysisAttemptUsage(10, null, 15);
		var noTotal = new IncidentAnalysisAttemptUsage(10, 5, null);
		return Stream.of(
				Arguments.of(noInput, all, new IncidentAnalysisAttemptUsage(null, 10, 30)),
				Arguments.of(all, noInput, new IncidentAnalysisAttemptUsage(null, 10, 30)),
				Arguments.of(noOutput, all, new IncidentAnalysisAttemptUsage(20, null, 30)),
				Arguments.of(all, noOutput, new IncidentAnalysisAttemptUsage(20, null, 30)),
				Arguments.of(noTotal, all, new IncidentAnalysisAttemptUsage(20, 10, null)),
				Arguments.of(all, noTotal, new IncidentAnalysisAttemptUsage(20, 10, null)),
				Arguments.of(IncidentAnalysisAttemptUsage.UNKNOWN, all, IncidentAnalysisAttemptUsage.UNKNOWN),
				Arguments.of(new IncidentAnalysisAttemptUsage(0, 0, 0), all, all),
				Arguments.of(new IncidentAnalysisAttemptUsage(Integer.MAX_VALUE, 0, 0), all,
						new IncidentAnalysisAttemptUsage(null, 5, 15)));
	}

	private IncidentAnalysisResult result(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
		return new IncidentAnalysisResult(analysis(), "fake", "model", "incident-analysis-v2",
				Instant.EPOCH, 25, inputTokens, outputTokens, totalTokens, 1);
	}

	private void verifyTwoCalls() {
		var ordered = inOrder(gateway);
		ordered.verify(gateway).analyze(input);
		ordered.verify(gateway).analyze(repair);
		verifyNoMoreInteractions(gateway);
	}
}
