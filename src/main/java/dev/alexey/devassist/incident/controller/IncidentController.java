package dev.alexey.devassist.incident.controller;

import dev.alexey.devassist.incident.dto.CreateIncidentRequestDTO;
import dev.alexey.devassist.incident.dto.IncidentResponseDTO;
import dev.alexey.devassist.incident.dto.IncidentPageResponseDTO;
import dev.alexey.devassist.incident.dto.UpdateIncidentStatusRequestDTO;
import dev.alexey.devassist.incident.dto.IncidentAnalysisResponseDTO;
import dev.alexey.devassist.incident.service.IncidentAnalysisService;
import dev.alexey.devassist.incident.service.IncidentService;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController implements IncidentApi {

	private final IncidentService service;
	private final IncidentAnalysisService analysisService;

	public IncidentController(IncidentService service, IncidentAnalysisService analysisService) {
		this.service = service;
		this.analysisService = analysisService;
	}

	@Override
	public ResponseEntity<IncidentResponseDTO> create(CreateIncidentRequestDTO request) {
		IncidentResponseDTO response = service.create(request.title(), request.description(), request.source());
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(response.id())
				.toUri();
		return ResponseEntity.created(location).body(response);
	}

	@Override
	public IncidentResponseDTO findById(UUID id) {
		return service.findById(id);
	}

	@Override
	public IncidentPageResponseDTO findAll(
			int page,
			int size) {
		return service.findAll(page, size);
	}

	@Override
	public IncidentResponseDTO updateStatus(UUID id,
			UpdateIncidentStatusRequestDTO request) {
		return service.updateStatus(id, request.status());
	}

	@Override
	public IncidentAnalysisResponseDTO analyze(UUID id) {
		return analysisService.analyze(id);
	}
}
