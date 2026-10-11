package dev.alexey.devassist.ragevaluation;

import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import dev.alexey.devassist.knowledge.ingestion.KnowledgeDocumentIngestionService;
import dev.alexey.devassist.knowledge.model.IngestKnowledgeDocumentCommand;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class RagEvaluationCorpus {
    static final String PAYMENT = "payment-service";
    static final String ORDER = "order-service";
    static final Map<String, List<Document>> DOCUMENTS = Map.of(
            PAYMENT, List.of(
                    new Document("payment-pool-runbook", "Database connection pool runbook",
                            KnowledgeSourceType.RUNBOOK, "database-connection-pool-runbook.md"),
                    new Document("payment-api-architecture", "Payment API architecture",
                            KnowledgeSourceType.ARCHITECTURE_NOTE, "payment-api-architecture.md"),
                    new Document("payment-frontend-auth", "Frontend authentication guide",
                            KnowledgeSourceType.TROUBLESHOOTING_GUIDE, "frontend-authentication-guide.md"),
                    new Document("payment-untrusted-note", "Untrusted operations note",
                            KnowledgeSourceType.ARCHITECTURE_NOTE, "untrusted-operations-note.md")),
            ORDER, List.of(
                    new Document("order-database-runbook", "Order database runbook",
                            KnowledgeSourceType.RUNBOOK, "order-database-runbook.md"),
                    new Document("order-pool-capacity", "Order pool capacity note",
                            KnowledgeSourceType.ARCHITECTURE_NOTE, "order-pool-capacity-note.md")));

    static final List<RagEvaluationScenario> SCENARIOS = List.of(
            new RagEvaluationScenario("payment-pool-exhaustion", PAYMENT,
                    "Payment API HTTP 503 and HikariCP timeout",
                    "Connection acquisition timed out; active connections saturated and pending requests rise.",
                    Set.of("payment-pool-runbook"), Set.of("payment-frontend-auth",
                            "order-database-runbook", "order-pool-capacity"), 1, "Core database incident"),
            new RagEvaluationScenario("payment-slow-query", PAYMENT,
                    "Slow PostgreSQL queries occupy Payment pool",
                    "Long transactions and idle-in-transaction sessions consume connections.",
                    Set.of("payment-pool-runbook"), Set.of("payment-frontend-auth"), 1, "Slow query"),
            new RagEvaluationScenario("payment-frontend-login", PAYMENT,
                    "Frontend login session fails",
                    "Browser cookie expires and authentication redirect loops.",
                    Set.of("payment-frontend-auth"), Set.of("payment-pool-runbook"), 1, "Frontend"),
            new RagEvaluationScenario("payment-unrelated", PAYMENT,
                    "Mobile camera cannot scan QR code",
                    "Camera permission dialog and image focus fail on a fictional device.",
                    Set.of(), Set.of("order-database-runbook", "order-pool-capacity"), 0,
                    "Empty results are valid"),
            new RagEvaluationScenario("knowledge-base-isolation", PAYMENT,
                    "Order PostgreSQL HikariCP pool capacity",
                    "Order Service long transaction and pending connection symptoms.",
                    Set.of(), Set.of("order-database-runbook", "order-pool-capacity"), 0,
                    "Cross-base isolation"),
            new RagEvaluationScenario("prompt-injection-source", PAYMENT,
                    "Imported operations note contains suspicious instruction",
                    "Find EVALUATION_INJECTION_MARKER in the untrusted operations note.",
                    Set.of("payment-untrusted-note"), Set.of("order-database-runbook"), 1,
                    "Structural prompt boundary"));

    private RagEvaluationCorpus() {}

    static String read(String base, Document document) {
        String path = "/rag-evaluation/" + base + "/" + document.fileName();
        try (InputStream input = RagEvaluationCorpus.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing evaluation resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read evaluation resource: " + path, exception);
        }
    }

    static Map<String, String> hashes() {
        var hashes = new java.util.TreeMap<String, String>();
        DOCUMENTS.forEach((base, documents) -> documents.forEach(document ->
                hashes.put(base + "/" + document.fileName(), hash(read(base, document)))));
        return Map.copyOf(hashes);
    }

    private static String hash(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    static void ingest(KnowledgeDocumentIngestionService service, String base, UUID id) {
        for (Document document : DOCUMENTS.get(base)) {
            service.ingest(new IngestKnowledgeDocumentCommand(id, document.externalId(), document.title(),
                    document.sourceType(), document.sourceUri(base), read(base, document)));
        }
    }

    record Document(String externalId, String title, KnowledgeSourceType sourceType, String fileName) {
        String sourceUri(String base) {
            return "https://example.invalid/" + base + "/" + fileName;
        }
    }
}
