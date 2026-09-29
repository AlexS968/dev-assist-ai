package dev.alexey.devassist.analysis;

import dev.alexey.devassist.analysis.exception.IncidentAnalysisConversionException;
import dev.alexey.devassist.analysis.exception.IncidentAnalysisValidationException;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Exactly one optional repair, using the same gateway. No transport retries or fallback. */
public final class IncidentAnalysisRepairPolicy {
	private final IncidentAnalysisGateway gateway;
	private final LongSupplier nanoTime;

	public IncidentAnalysisRepairPolicy(IncidentAnalysisGateway gateway, LongSupplier nanoTime) {
		this.gateway = gateway;
		this.nanoTime = nanoTime;
	}

	public IncidentAnalysisResult analyze(IncidentAnalysisInput incident) {
		long started = nanoTime.getAsLong();
		IncidentAnalysisAttemptUsage firstUsage;
		try {
			return gateway.analyze(new IncidentAnalysisInput(incident.title(), incident.description()));
		}
		catch (IncidentAnalysisConversionException exception) {
			firstUsage = exception.usage();
		}
		catch (IncidentAnalysisValidationException exception) {
			firstUsage = exception.usage();
		}
		// Outside the catch scope: any failure on this final attempt propagates unchanged.
		var repaired = gateway.analyze(new IncidentAnalysisInput(incident.title(), incident.description(), true));
		long latencyMs = TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - started);
		return new IncidentAnalysisResult(repaired.analysis(), repaired.provider(), repaired.model(),
				repaired.promptVersion(), repaired.generatedAt(), latencyMs,
				sum(firstUsage.inputTokens(), repaired.inputTokens()),
				sum(firstUsage.outputTokens(), repaired.outputTokens()),
				sum(firstUsage.totalTokens(), repaired.totalTokens()), 2);
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
