package dev.alexey.devassist.ragevaluation;

import dev.alexey.devassist.incident.entity.Incident;
import dev.alexey.devassist.analysis.grounding.GroundingProperties;
import dev.alexey.devassist.analysis.grounding.IncidentAnalysisContextSelector;
import dev.alexey.devassist.analysis.infrastructure.AiProperties;
import dev.alexey.devassist.incident.enums.IncidentSource;
import dev.alexey.devassist.incident.repository.IncidentRepository;
import dev.alexey.devassist.incident.service.IncidentAnalysisService;
import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingOrchestrator;
import dev.alexey.devassist.knowledge.embedding.infrastructure.EmbeddingProperties;
import dev.alexey.devassist.knowledge.entity.KnowledgeBase;
import dev.alexey.devassist.knowledge.ingestion.KnowledgeDocumentIngestionService;
import dev.alexey.devassist.knowledge.repository.KnowledgeBaseRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeChunkRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeDocumentRepository;
import dev.alexey.devassist.knowledge.retrieval.KnowledgeRetrievalRequest;
import dev.alexey.devassist.knowledge.retrieval.KnowledgeRetrievalResult;
import dev.alexey.devassist.knowledge.retrieval.KnowledgeRetrievalService;
import dev.alexey.devassist.knowledge.retrieval.RetrievalProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Explicit, paid, nondeterministic integration run. Never part of default verify. */
@Tag("live-rag")
@Testcontainers
@SpringBootTest
@Import(LiveOpenAiEmbeddingOptionsConfiguration.class)
class LiveRagEvaluationTests {
    private static final String SMOKE_TITLE = "Payment API HTTP 503 with HikariCP connection acquisition timeout";
    private static final String SMOKE_DESCRIPTION = "Active database connections are exhausted and pending requests rise.";
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg17-bookworm")
                    .asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void liveProperties(DynamicPropertyRegistry registry) {
        LiveRagProviderConfiguration.register(System.getenv(), registry);
    }

    @Autowired KnowledgeBaseRepository bases;
    @Autowired KnowledgeDocumentRepository documents;
    @Autowired KnowledgeChunkRepository chunks;
    @Autowired KnowledgeDocumentIngestionService ingestion;
    @Autowired KnowledgeEmbeddingOrchestrator embeddings;
    @Autowired KnowledgeRetrievalService retrieval;
    @Autowired RetrievalProperties retrievalProperties;
    @Autowired GroundingProperties groundingProperties;
    @Autowired EmbeddingProfile profile;
    @Autowired AiProperties aiProperties;
    @Autowired EmbeddingProperties embeddingProperties;
    @Autowired Environment environment;
    @Autowired IncidentRepository incidents;
    @Autowired IncidentAnalysisService analysis;

    @Test void evaluateSelectedProvider() throws IOException {
        String mode = required("RAG_LIVE_PROVIDER");
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("timestamp", Instant.now().toString());
        report.put("provider", mode);
        LiveRagEvaluationRunner.run(mode, report, () -> evaluate(mode, report),
                LiveRagEvaluationTests::writeReport);
    }

    private void evaluate(String mode, Map<String, Object> report) {
        assertThat(aiProperties.provider()).isEqualTo(mode);
        assertThat(embeddingProperties.provider()).isEqualTo(mode);
        assertThat(aiProperties.promptVersion()).isEqualTo("incident-analysis-v3");
        assertThat(aiProperties.maxOutputTokens()).isEqualTo(1000);
        assertThat(profile.dimensions()).isEqualTo(768);
        assertThat(environment.getProperty("app.rag.enabled", Boolean.class)).isTrue();
        report.put("stage", "corpus_ingestion");
        Map<String, KnowledgeBase> corpus = createCorpus();
        report.put("stage", "embedding");
        embedAll(corpus);
        report.put("stage", "retrieval");
        List<RagEvaluationMetrics.ScenarioResult> observations = new ArrayList<>();
        List<Map<String, Object>> scenarioReports = new ArrayList<>();
        for (RagEvaluationScenario scenario : RagEvaluationCorpus.SCENARIOS) {
            KnowledgeBase base = corpus.get(scenario.knowledgeBaseKey());
            KnowledgeRetrievalResult result = retrieval.retrieve(new KnowledgeRetrievalRequest(
                    base.getId(), scenario.query(), retrievalProperties.maxTopK(), 0.0));
            assertRetrievalIsolation(base, result);
            observations.add(new RagEvaluationMetrics.ScenarioResult(scenario, result.matches()));
            scenarioReports.add(scenarioReport(scenario, result));
        }
        report.put("scenarios", scenarioReports);
        report.put("thresholdSweep", RagEvaluationMetrics.sweep(observations, retrievalProperties.topK()));
        report.put("chatModel", mode.equals("openai")
                ? aiProperties.openAiModel() : aiProperties.ollamaModel());
        report.put("embeddingModel", profile.model());
        report.put("dimensions", profile.dimensions());
        report.put("promptVersion", "incident-analysis-v3");
        report.put("documentCount", documents.count());
        report.put("chunkCount", chunks.count());
        report.put("configuration", Map.of("topK", retrievalProperties.maxTopK(),
                "productionTopK", retrievalProperties.topK(),
                "productionThreshold", retrievalProperties.thresholdFor(profile),
                "maxContextChars", groundingProperties.maxContextChars(),
                "maxChunks", groundingProperties.maxChunks()));
        report.put("stage", "grounded_smoke_probe");
        KnowledgeBase payment = corpus.get(RagEvaluationCorpus.PAYMENT);
        KnowledgeRetrievalResult smokeProbe = retrieval.retrieve(new KnowledgeRetrievalRequest(
                payment.getId(), SMOKE_TITLE + "\n" + SMOKE_DESCRIPTION,
                retrievalProperties.maxTopK(), 0.0));
        report.put("groundedSmokeRetrievalProbe", LiveRagSmokeRetrievalProbe.snapshot(smokeProbe,
                retrievalProperties.thresholdFor(profile), retrievalProperties.topK(),
                groundingProperties.maxChunks(), groundingProperties.maxContextChars()));
        assertRetrievalIsolation(payment, smokeProbe);
        report.put("stage", "grounded_smoke");
        var smoke = groundedSmoke(payment, report);
        var injection = observations.stream().filter(observation ->
                observation.scenario().scenarioId().equals("prompt-injection-source")).findFirst().orElseThrow();
        var selectedInjection = new IncidentAnalysisContextSelector().select(injection.matches(),
                groundingProperties.maxChunks(), groundingProperties.maxContextChars());
        boolean markerRetrieved = injection.matches().stream().anyMatch(chunk ->
                chunk.content().contains("EVALUATION_INJECTION_MARKER"));
        boolean markerSelected = selectedInjection.sources().stream().anyMatch(source ->
                source.chunk().content().contains("EVALUATION_INJECTION_MARKER"));
        assertThat(selectedInjection.sources()).noneMatch(source -> source.sourceId().equals("S999"));
        report.put("gitCommit", gitCommit());
        report.put("corpusHashes", RagEvaluationCorpus.hashes());
        report.put("meanReciprocalRank", observations.stream().mapToDouble(observation ->
                RagEvaluationMetrics.evaluate(observation.scenario(), observation.matches(),
                        retrievalProperties.topK()).reciprocalRank()).average().orElse(0));
        report.put("emptyResults", observations.stream().filter(observation ->
                observation.matches().isEmpty()).count());
        report.put("forbiddenHits", scenarioReports.stream().mapToLong(row ->
                (long) row.get("forbiddenHits")).sum());
        report.put("queryInputTokensKnown", scenarioReports.stream().map(row ->
                row.get("queryInputTokens")).filter(java.util.Objects::nonNull).count());
        var relevantScores = observations.stream().flatMap(observation -> observation.matches().stream()
                .filter(chunk -> observation.scenario().expectedRelevantExternalIds()
                        .contains(chunk.externalId())).mapToDouble(chunk -> chunk.similarity()).boxed()).toList();
        Map<String, Object> scoreSummary = new LinkedHashMap<>();
        scoreSummary.put("min", relevantScores.stream().mapToDouble(Double::doubleValue).min().isPresent()
                ? relevantScores.stream().mapToDouble(Double::doubleValue).min().getAsDouble() : null);
        scoreSummary.put("max", relevantScores.stream().mapToDouble(Double::doubleValue).max().isPresent()
                ? relevantScores.stream().mapToDouble(Double::doubleValue).max().getAsDouble() : null);
        scoreSummary.put("mean", relevantScores.stream().mapToDouble(Double::doubleValue).average().isPresent()
                ? relevantScores.stream().mapToDouble(Double::doubleValue).average().getAsDouble() : null);
        report.put("relevantSimilarity", scoreSummary);
        report.put("averageRetrievalLatencyMs", scenarioReports.stream().mapToLong(row ->
                (long) row.get("retrievalLatencyMs")).average().orElse(0));
        report.put("groundedSmoke", smoke);
        report.put("injectionObservation", Map.of("markerRetrieved", markerRetrieved,
                "markerSelected", markerSelected, "sourceIdsBackendAssigned", true,
                "fabricatedCitationAccepted", false));
        report.put("warnings", List.of("Small fictional corpus; one run is not a benchmark.",
                "Citation presence does not prove document correctness or factual accuracy.",
                "Prompt injection mitigation is structural, not an absolute guarantee."));
    }

    private Map<String, KnowledgeBase> createCorpus() {
        Map<String, KnowledgeBase> corpus = new LinkedHashMap<>();
        for (String key : List.of(RagEvaluationCorpus.PAYMENT, RagEvaluationCorpus.ORDER)) {
            KnowledgeBase base = bases.saveAndFlush(new KnowledgeBase(
                    "live-" + key + "-" + UUID.randomUUID(), key, "Fictional evaluation corpus"));
            RagEvaluationCorpus.ingest(ingestion, key, base.getId());
            corpus.put(key, base);
        }
        return corpus;
    }

    private void embedAll(Map<String, KnowledgeBase> corpus) {
        for (KnowledgeBase base : corpus.values()) {
            var run = embeddings.embedKnowledgeBase(base.getId());
            assertThat(run.limitReached()).isFalse();
            var baseDocuments = documents.findAll().stream().filter(document ->
                    document.getKnowledgeBase().getId().equals(base.getId())).toList();
            assertThat(baseDocuments).isNotEmpty();
            for (var document : baseDocuments) {
                for (var chunk : chunks.findByDocument_IdOrderByChunkIndexAsc(document.getId())) {
                    assertThat(chunk.getEmbedding()).hasSize(profile.dimensions());
                    assertThat(profile.matches(chunk.getEmbeddingProvider(), chunk.getEmbeddingModel(),
                            chunk.getEmbeddingDimensions())).isTrue();
                }
            }
        }
    }

    private void assertRetrievalIsolation(KnowledgeBase base, KnowledgeRetrievalResult result) {
        assertThat(result.knowledgeBaseId()).isEqualTo(base.getId());
        assertThat(result.activeProfile()).isEqualTo(profile);
        for (var chunk : result.matches()) {
            var document = documents.findById(chunk.documentId()).orElseThrow();
            assertThat(document.getKnowledgeBase().getId()).isEqualTo(base.getId());
            assertThat(document.getExternalId()).isEqualTo(chunk.externalId());
            var stored = chunks.findById(chunk.chunkId()).orElseThrow();
            assertThat(profile.matches(stored.getEmbeddingProvider(), stored.getEmbeddingModel(),
                    stored.getEmbeddingDimensions())).isTrue();
            assertThat(stored.getContent()).isEqualTo(chunk.content());
        }
    }

    private Map<String, Object> scenarioReport(RagEvaluationScenario scenario, KnowledgeRetrievalResult result) {
        List<Map<String, Object>> matches = result.matches().stream().map(chunk ->
                Map.<String, Object>of("externalId", chunk.externalId(), "chunkId", chunk.chunkId(),
                        "similarity", chunk.similarity())).toList();
        long forbidden = result.matches().stream().filter(chunk ->
                scenario.forbiddenExternalIds().contains(chunk.externalId())).count();
        if (scenario.scenarioId().equals("knowledge-base-isolation")) assertThat(forbidden).isZero();
        var quality = RagEvaluationMetrics.evaluate(scenario, result.matches(), retrievalProperties.topK());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("scenarioId", scenario.scenarioId());
        row.put("matches", matches);
        row.put("hitAt1", quality.hitAt1());
        row.put("hitAt3", quality.hitAt3());
        row.put("recallAtK", quality.recallAtK());
        row.put("reciprocalRank", quality.reciprocalRank());
        row.put("forbiddenHits", forbidden);
        row.put("retrievalLatencyMs", result.totalLatencyMs());
        row.put("queryInputTokens", result.inputTokens());
        row.put("qualityObservation", quality.hitAt1() ? "expected source ranked first" :
                "expected source not ranked first or no expected source");
        return row;
    }

    private Map<String, Object> groundedSmoke(KnowledgeBase payment, Map<String, Object> report) {
        Incident incident = new Incident(SMOKE_TITLE, SMOKE_DESCRIPTION, IncidentSource.API);
        incident.assignKnowledgeBase(payment);
        var response = analysis.analyze(incidents.saveAndFlush(incident).getId());
        report.put("groundedSmoke", LiveRagSmokeReport.snapshot(
                response, retrievalProperties.thresholdFor(profile)));
        assertThat(response.promptVersion()).isEqualTo("incident-analysis-v3");
        assertThat(response.groundingStatus().name()).isEqualTo("CONTEXT_PROVIDED");
        assertThat(response.retrievedChunkCount()).isPositive();
        assertThat(response.providedContextChunkCount()).isPositive();
        assertThat(response.citations()).isNotEmpty();
        assertThat(response.grounded()).isTrue();
        assertThat(response.attemptCount()).isBetween(1, 2);
        if (response.outputTokens() != null) {
            assertThat(response.outputTokens()).isLessThanOrEqualTo(aiProperties.maxOutputTokens());
        }
        assertThat(response.citations()).noneMatch(citation -> citation.sourceId().equals("S999"));
        for (var citation : response.citations()) {
            assertThat(citation.sourceId()).matches("S[1-9][0-9]*");
            var document = documents.findById(citation.documentId()).orElseThrow();
            assertThat(document.getKnowledgeBase().getId()).isEqualTo(payment.getId());
            assertThat(chunks.existsById(citation.chunkId())).isTrue();
        }
        String rendered = JsonMapper.shared().writeValueAsString(response);
        if ("openai".equals(required("RAG_LIVE_PROVIDER"))) {
            assertThat(rendered).as("Response must not contain provider credentials")
                    .doesNotContain(required("OPENAI_API_KEY"));
        }
        Map<String, Object> smoke = new LinkedHashMap<>();
        smoke.put("groundingStatus", response.groundingStatus());
        smoke.put("grounded", response.grounded());
        smoke.put("retrievedChunkCount", response.retrievedChunkCount());
        smoke.put("providedContextChunkCount", response.providedContextChunkCount());
        smoke.put("retrievalLatencyMs", response.retrievalLatencyMs());
        smoke.put("generationLatencyMs", response.latencyMs());
        smoke.put("retrievalInputTokens", response.retrievalInputTokens());
        smoke.put("generationInputTokens", response.inputTokens());
        smoke.put("generationOutputTokens", response.outputTokens());
        smoke.put("attemptCount", response.attemptCount());
        smoke.put("citations", response.citations());
        return smoke;
    }

    private static void writeReport(String mode, Map<String, Object> report) throws IOException {
        Path directory = Path.of("target", "rag-evaluation");
        Files.createDirectories(directory);
        JsonMapper.shared().writeValue(directory.resolve(mode + "-report.json").toFile(), report);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing live setting: " + name);
        return value;
    }

    private static String gitCommit() {
        try {
            Path head = Path.of(".git", "HEAD");
            String value = Files.readString(head).trim();
            if (!value.startsWith("ref: ")) return value;
            Path ref = Path.of(".git", value.substring(5));
            return Files.exists(ref) ? Files.readString(ref).trim() : "unavailable";
        } catch (IOException exception) {
            return "unavailable";
        }
    }
}
