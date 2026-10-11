package dev.alexey.devassist.ragevaluation;

import java.io.IOException;
import java.util.Map;

/** Writes a sanitized report on success, evaluation errors and failed assertions. */
final class LiveRagEvaluationRunner {
    private LiveRagEvaluationRunner() {}

    static void run(String mode, Map<String, Object> report, Runnable evaluation,
            ReportWriter writer) throws IOException {
        try {
            evaluation.run();
            report.put("status", "SUCCESS");
            report.remove("stage");
        } catch (RuntimeException | AssertionError failure) {
            report.put("status", "FAILED");
            report.put("failure", LiveRagFailureReport.fields(failure, mode,
                    (String) report.getOrDefault("stage", "preflight")));
            try {
                writer.write(mode, report);
            } catch (IOException reportFailure) {
                failure.addSuppressed(reportFailure);
            }
            throw failure;
        }
        writer.write(mode, report);
    }

    @FunctionalInterface
    interface ReportWriter {
        void write(String mode, Map<String, Object> report) throws IOException;
    }
}
