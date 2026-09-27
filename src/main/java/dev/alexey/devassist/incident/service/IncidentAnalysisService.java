package dev.alexey.devassist.incident.service;

import dev.alexey.devassist.analysis.IncidentAnalysisGateway;
import dev.alexey.devassist.analysis.IncidentAnalysisInput;
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
	private final IncidentAnalysisGateway gateway;
	private final IncidentMapper mapper;

	public IncidentAnalysisService(IncidentRepository repository, IncidentAnalysisGateway gateway,
			IncidentMapper mapper) {
		this.repository = repository;
		this.gateway = gateway;
		this.mapper = mapper;
	}

	// Suspend any caller transaction. The repository read completes its own transaction
	// before the potentially slow provider call. No result is written to the database.
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public IncidentAnalysisResponseDTO analyze(UUID id) {
		var incident = repository.findById(id).orElseThrow(() -> new IncidentNotFoundException(id));
		var input = new IncidentAnalysisInput(incident.getTitle(), incident.getDescription());
		return mapper.toAnalysisResponse(gateway.analyze(input));
	}
}
