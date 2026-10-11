package dev.alexey.devassist.incident.service;

import dev.alexey.devassist.analysis.IncidentAnalysisRepairPolicy;
import dev.alexey.devassist.analysis.IncidentAnalysisInput;
import dev.alexey.devassist.analysis.grounding.IncidentAnalysisGroundingService;
import dev.alexey.devassist.analysis.grounding.IncidentAnalysisCitationResolver;
import dev.alexey.devassist.incident.dto.IncidentAnalysisResponseDTO;
import dev.alexey.devassist.incident.exception.IncidentNotFoundException;
import dev.alexey.devassist.incident.mapper.IncidentMapper;
import dev.alexey.devassist.incident.repository.IncidentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentAnalysisService {

	private final IncidentRepository repository;
	private final IncidentAnalysisRepairPolicy repairPolicy;
	private final IncidentMapper mapper;
    private final IncidentAnalysisGroundingService groundingService;
    private final IncidentAnalysisCitationResolver citationResolver = new IncidentAnalysisCitationResolver();

	public IncidentAnalysisService(IncidentRepository repository, IncidentAnalysisRepairPolicy repairPolicy,
			IncidentMapper mapper, IncidentAnalysisGroundingService groundingService) {
		this.repository = repository;
		this.repairPolicy = repairPolicy;
		this.mapper = mapper;
        this.groundingService = groundingService;
	}

	// Suspend any caller transaction. The repository read completes its own transaction
	// before the potentially slow provider call. No result is written to the database.
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public IncidentAnalysisResponseDTO analyze(UUID id) {
		var incident = repository.findById(id).orElseThrow(() -> new IncidentNotFoundException(id));
		var knowledgeBaseId = incident.getKnowledgeBase() == null ? null : incident.getKnowledgeBase().getId();
        var grounding = groundingService.prepare(knowledgeBaseId, incident.getTitle(), incident.getDescription());
        var input = new IncidentAnalysisInput(incident.getTitle(), incident.getDescription(), false, grounding.context());
        var result = repairPolicy.analyze(input,
                candidate -> citationResolver.validate(candidate.analysis(), grounding.context()));
        var citations = citationResolver.resolve(result.analysis(), grounding.context(), knowledgeBaseId)
                .stream().map(mapper::toCitationResponse).toList();
        return mapper.toAnalysisResponse(result, grounding, citations);
	}
}
