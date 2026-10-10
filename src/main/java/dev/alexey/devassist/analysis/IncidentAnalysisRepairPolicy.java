package dev.alexey.devassist.analysis;

import dev.alexey.devassist.analysis.exception.IncidentAnalysisConversionException;
import dev.alexey.devassist.analysis.exception.IncidentAnalysisValidationException;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Consumer;

/** Exactly one optional repair, using the same gateway. No transport retries or fallback. */
public final class IncidentAnalysisRepairPolicy {
	private final IncidentAnalysisGateway gateway;
	private final LongSupplier nanoTime;

	public IncidentAnalysisRepairPolicy(IncidentAnalysisGateway gateway, LongSupplier nanoTime) {
		this.gateway = gateway;
		this.nanoTime = nanoTime;
	}

	public IncidentAnalysisResult analyze(IncidentAnalysisInput incident) {
		return analyze(incident, result -> {});
	}

	public IncidentAnalysisResult analyze(IncidentAnalysisInput incident,
			Consumer<IncidentAnalysisResult> semanticValidation) {
		long started = nanoTime.getAsLong();
		IncidentAnalysisAttemptUsage firstUsage;
		try {
			return attempt(incident, semanticValidation);
		}
		catch (IncidentAnalysisConversionException exception) {
			firstUsage = exception.usage();
		}
		catch (IncidentAnalysisValidationException exception) {
			firstUsage = exception.usage();
		}
		// Outside the catch scope: any failure on this final attempt propagates unchanged.
		var repaired = attempt(new IncidentAnalysisInput(incident.title(), incident.description(), true,
				incident.context()), semanticValidation);
		long latencyMs = TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - started);
		return new IncidentAnalysisResult(repaired.analysis(), repaired.provider(), repaired.model(),
				repaired.promptVersion(), repaired.generatedAt(), latencyMs,
				sum(firstUsage.inputTokens(), repaired.inputTokens()),
				sum(firstUsage.outputTokens(), repaired.outputTokens()),
				sum(firstUsage.totalTokens(), repaired.totalTokens()), 2);
	}

	private IncidentAnalysisResult attempt(IncidentAnalysisInput input,
			Consumer<IncidentAnalysisResult> validation) {
		var result = gateway.analyze(input);
		try {
			validation.accept(result);
		}
		catch (IncidentAnalysisValidationException exception) {
			throw new IncidentAnalysisValidationException(exception.violations(),
					new IncidentAnalysisAttemptUsage(result.inputTokens(), result.outputTokens(), result.totalTokens()));
		}
		return result;
	}

	private static Integer sum(Integer first, Integer second) {
		if (first == null || second == null) {
			return null;
		}
		long total = (long) first + second;
		// Preserve the public Integer contract without returning wrapped/partial counts.
		return total > Integer.MAX_VALUE ? null : (int) total;
	}
}
