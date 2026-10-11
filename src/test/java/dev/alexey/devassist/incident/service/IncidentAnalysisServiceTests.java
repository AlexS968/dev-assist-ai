package dev.alexey.devassist.incident.service;

import dev.alexey.devassist.analysis.IncidentAnalysisGateway;
import dev.alexey.devassist.analysis.IncidentAnalysisInput;
import dev.alexey.devassist.analysis.IncidentAnalysisResult;
import static dev.alexey.devassist.analysis.StructuredAnalysisFixtures.analysis;
import dev.alexey.devassist.incident.entity.Incident;
import dev.alexey.devassist.incident.enums.IncidentSource;
import dev.alexey.devassist.incident.exception.IncidentNotFoundException;
import dev.alexey.devassist.incident.mapper.IncidentMapper;
import dev.alexey.devassist.incident.repository.IncidentRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentAnalysisServiceTests {

	private final IncidentRepository repository = mock(IncidentRepository.class);
	private final IncidentAnalysisGateway gateway = mock(IncidentAnalysisGateway.class);
	private final IncidentAnalysisService service = new IncidentAnalysisService(repository, new dev.alexey.devassist.analysis.IncidentAnalysisRepairPolicy(gateway, System::nanoTime),
			Mappers.getMapper(IncidentMapper.class),
            new dev.alexey.devassist.analysis.grounding.IncidentAnalysisGroundingService(null, null, false));

	@Test
	void loadsIncidentPassesOnlyTitleAndDescriptionAndMapsAllResultFields() {
		UUID id = UUID.randomUUID();
		var incident = new Incident("Title", "Description", IncidentSource.MONITORING);
		when(repository.findById(id)).thenReturn(Optional.of(incident));
		var input = new IncidentAnalysisInput("Title", "Description");
		var result = new IncidentAnalysisResult(analysis(), "fake", "test-model", "incident-analysis-v3",
				Instant.parse("2026-09-27T12:00:00Z"), 125, 12, 8, 20, 1);
		when(gateway.analyze(input)).thenReturn(result);

		var response = service.analyze(id);
        assertThat(response.analysis().summary()).isEqualTo(result.analysis().summary());
        assertThat(response.groundingStatus()).isEqualTo(dev.alexey.devassist.analysis.grounding.IncidentGroundingStatus.RAG_DISABLED);
        assertThat(response.citations()).isEmpty();

		var order = inOrder(repository, gateway);
		order.verify(repository).findById(id);
		order.verify(gateway).analyze(input);
		verifyNoMoreInteractions(repository, gateway);
	}

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = dev.alexey.devassist.analysis.grounding.IncidentGroundingStatus.class,
            names = {"RAG_DISABLED", "NO_KNOWLEDGE_BASE", "NO_RELEVANT_CONTEXT", "RETRIEVAL_FAILED"})
    void noContextStatusesCallChatOnceWithEmptyCitations(
            dev.alexey.devassist.analysis.grounding.IncidentGroundingStatus status) {
        UUID id = UUID.randomUUID();
        var incident = new Incident("Title", "Description", IncidentSource.MONITORING);
        when(repository.findById(id)).thenReturn(Optional.of(incident));
        var grounding = mock(dev.alexey.devassist.analysis.grounding.IncidentAnalysisGroundingService.class);
        var decision = dev.alexey.devassist.analysis.grounding.GroundingDecision.withoutRetrieval(status, null);
        when(grounding.prepare(null, "Title", "Description")).thenReturn(decision);
        var candidate = new IncidentAnalysisResult(analysis(), "fake", "model", "incident-analysis-v3",
                Instant.EPOCH, 10, 5, 3, 8, 1);
        when(gateway.analyze(new IncidentAnalysisInput("Title", "Description"))).thenReturn(candidate);
        var localService = new IncidentAnalysisService(repository,
                new dev.alexey.devassist.analysis.IncidentAnalysisRepairPolicy(gateway, System::nanoTime),
                Mappers.getMapper(IncidentMapper.class), grounding);
        var response = localService.analyze(id);
        assertThat(response.groundingStatus()).isEqualTo(status);
        assertThat(response.grounded()).isFalse();
        assertThat(response.citations()).isEmpty();
        assertThat(response.retrievalInputTokens()).isNull();
        assertThat(response.retrievalLatencyMs()).isZero();
        assertThat(response.providedContextChunkCount()).isZero();
        verify(gateway, times(1)).analyze(new IncidentAnalysisInput("Title", "Description"));
    }

    @Test
    void providedContextWithoutCitationIsNotGrounded() {
        UUID id = UUID.randomUUID();
        var incident = new Incident("Title", "Description", IncidentSource.MONITORING);
        when(repository.findById(id)).thenReturn(Optional.of(incident));
        var chunk = new dev.alexey.devassist.knowledge.retrieval.RetrievedKnowledgeChunk(
                UUID.randomUUID(), UUID.randomUUID(), "ext", "Runbook",
                dev.alexey.devassist.knowledge.enums.KnowledgeSourceType.RUNBOOK, null,
                0, "evidence", java.util.Map.of(), 0.9);
        var context = new dev.alexey.devassist.analysis.grounding.IncidentAnalysisContext(
                java.util.List.of(new dev.alexey.devassist.analysis.grounding.IncidentAnalysisSource("S1", chunk)), 8);
        var grounding = mock(dev.alexey.devassist.analysis.grounding.IncidentAnalysisGroundingService.class);
        when(grounding.prepare(null, "Title", "Description")).thenReturn(
                new dev.alexey.devassist.analysis.grounding.GroundingDecision(
                        dev.alexey.devassist.analysis.grounding.IncidentGroundingStatus.CONTEXT_PROVIDED,
                        null, context, 5, 2, 1));
        when(gateway.analyze(new IncidentAnalysisInput("Title", "Description", false, context)))
                .thenReturn(new IncidentAnalysisResult(analysis(), "fake", "model", "incident-analysis-v3",
                        Instant.EPOCH, 10, 5, 3, 8, 1));
        var localService = new IncidentAnalysisService(repository,
                new dev.alexey.devassist.analysis.IncidentAnalysisRepairPolicy(gateway, System::nanoTime),
                Mappers.getMapper(IncidentMapper.class), grounding);
        var response = localService.analyze(id);
        assertThat(response.groundingStatus()).isEqualTo(
                dev.alexey.devassist.analysis.grounding.IncidentGroundingStatus.CONTEXT_PROVIDED);
        assertThat(response.grounded()).isFalse();
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void providedContextProducesBackendCitationAndSeparateMetrics() {
        UUID id = UUID.randomUUID();
        UUID baseId = UUID.randomUUID();
        var base = mock(dev.alexey.devassist.knowledge.entity.KnowledgeBase.class);
        when(base.getId()).thenReturn(baseId);
        var incident = new Incident("Title", "Description", IncidentSource.MONITORING);
        incident.assignKnowledgeBase(base);
        when(repository.findById(id)).thenReturn(Optional.of(incident));
        var chunk = new dev.alexey.devassist.knowledge.retrieval.RetrievedKnowledgeChunk(
                UUID.randomUUID(), UUID.randomUUID(), "external", "Runbook",
                dev.alexey.devassist.knowledge.enums.KnowledgeSourceType.RUNBOOK, null,
                0, "untrusted evidence", java.util.Map.of(), 0.92);
        var context = new dev.alexey.devassist.analysis.grounding.IncidentAnalysisContext(
                java.util.List.of(new dev.alexey.devassist.analysis.grounding.IncidentAnalysisSource("S1", chunk)), 18);
        var decision = new dev.alexey.devassist.analysis.grounding.GroundingDecision(
                dev.alexey.devassist.analysis.grounding.IncidentGroundingStatus.CONTEXT_PROVIDED,
                baseId, context, 12, 7, 1);
        var grounding = mock(dev.alexey.devassist.analysis.grounding.IncidentAnalysisGroundingService.class);
        when(grounding.prepare(baseId, "Title", "Description")).thenReturn(decision);
        var candidate = analysis();
        var cited = new dev.alexey.devassist.analysis.model.StructuredIncidentAnalysis(candidate.summary(),
                candidate.probableCauses(), candidate.investigationSteps(), candidate.uncertainties(),
                java.util.List.of("S1"));
        when(gateway.analyze(new IncidentAnalysisInput("Title", "Description", false, context)))
                .thenReturn(new IncidentAnalysisResult(cited, "fake", "model", "incident-analysis-v3",
                        Instant.EPOCH, 100, 20, 10, 30, 1));
        var groundedService = new IncidentAnalysisService(repository,
                new dev.alexey.devassist.analysis.IncidentAnalysisRepairPolicy(gateway, System::nanoTime),
                Mappers.getMapper(IncidentMapper.class), grounding);
        var response = groundedService.analyze(id);
        assertThat(response.groundingStatus()).isEqualTo(
                dev.alexey.devassist.analysis.grounding.IncidentGroundingStatus.CONTEXT_PROVIDED);
        assertThat(response.grounded()).isTrue();
        assertThat(response.knowledgeBaseId()).isEqualTo(baseId);
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().getFirst().chunkId()).isEqualTo(chunk.chunkId());
        assertThat(response.citations().getFirst().documentTitle()).isEqualTo("Runbook");
        assertThat(response.retrievalInputTokens()).isEqualTo(7);
        assertThat(response.inputTokens()).isEqualTo(20);
        assertThat(response.retrievalLatencyMs()).isEqualTo(12);
        assertThat(response.toString()).doesNotContain(chunk.content());
        verify(gateway, times(1)).analyze(any());
        verify(grounding, times(1)).prepare(baseId, "Title", "Description");
    }

	@Test
	void unknownIncidentDoesNotCallGateway() {
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.analyze(id)).isInstanceOf(IncidentNotFoundException.class)
				.hasMessage("Incident with id " + id + " was not found.");
		verifyNoInteractions(gateway);
	}
}
