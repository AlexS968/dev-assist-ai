package dev.alexey.devassist.incident.mapper;

import dev.alexey.devassist.analysis.IncidentAnalysisResult;
import dev.alexey.devassist.analysis.model.StructuredIncidentAnalysis;
import dev.alexey.devassist.analysis.model.ProbableCause;
import dev.alexey.devassist.analysis.model.InvestigationStep;
import dev.alexey.devassist.incident.dto.StructuredIncidentAnalysisDTO;
import dev.alexey.devassist.incident.dto.ProbableCauseDTO;
import dev.alexey.devassist.incident.dto.InvestigationStepDTO;
import dev.alexey.devassist.incident.dto.IncidentAnalysisResponseDTO;

import dev.alexey.devassist.incident.dto.IncidentResponseDTO;
import dev.alexey.devassist.incident.dto.IncidentPageResponseDTO;
import dev.alexey.devassist.incident.entity.Incident;

import java.util.List;

import org.springframework.data.domain.Page;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IncidentMapper {

	@Mapping(source = "knowledgeBase.id", target = "knowledgeBaseId")
	IncidentResponseDTO toResponse(Incident incident);

    @Mapping(source = "result.analysis", target = "analysis")
    @Mapping(source = "result.provider", target = "provider")
    @Mapping(source = "result.model", target = "model")
    @Mapping(source = "result.promptVersion", target = "promptVersion")
    @Mapping(source = "result.generatedAt", target = "generatedAt")
    @Mapping(source = "result.latencyMs", target = "latencyMs")
    @Mapping(source = "result.inputTokens", target = "inputTokens")
    @Mapping(source = "result.outputTokens", target = "outputTokens")
    @Mapping(source = "result.totalTokens", target = "totalTokens")
    @Mapping(source = "result.attemptCount", target = "attemptCount")
    @Mapping(source = "grounding.status", target = "groundingStatus")
    @Mapping(expression = "java(!citations.isEmpty())", target = "grounded")
    @Mapping(source = "grounding.knowledgeBaseId", target = "knowledgeBaseId")
    @Mapping(source = "grounding.retrievalLatencyMs", target = "retrievalLatencyMs")
    @Mapping(source = "grounding.retrievalInputTokens", target = "retrievalInputTokens")
    @Mapping(source = "grounding.retrievedChunkCount", target = "retrievedChunkCount")
    @Mapping(expression = "java(grounding.providedContextChunkCount())", target = "providedContextChunkCount")
    @Mapping(expression = "java(grounding.providedContextCharacters())", target = "providedContextCharacters")
    IncidentAnalysisResponseDTO toAnalysisResponse(IncidentAnalysisResult result,
            dev.alexey.devassist.analysis.grounding.GroundingDecision grounding,
            List<dev.alexey.devassist.incident.dto.IncidentAnalysisCitationDTO> citations);

    dev.alexey.devassist.incident.dto.IncidentAnalysisCitationDTO toCitationResponse(
            dev.alexey.devassist.analysis.grounding.IncidentAnalysisCitation citation);


	StructuredIncidentAnalysisDTO toStructuredAnalysis(StructuredIncidentAnalysis analysis);

	ProbableCauseDTO toProbableCause(ProbableCause cause);

	InvestigationStepDTO toInvestigationStep(InvestigationStep step);

	List<IncidentResponseDTO> toResponses(List<Incident> incidents);

	@Mapping(source = "content", target = "items", defaultExpression = "java(java.util.List.of())")
	@Mapping(source = "number", target = "page")
	@Mapping(source = "size", target = "size")
	@Mapping(source = "totalElements", target = "totalElements")
	@Mapping(source = "totalPages", target = "totalPages")
	IncidentPageResponseDTO toPageResponse(Page<Incident> incidents);
}
