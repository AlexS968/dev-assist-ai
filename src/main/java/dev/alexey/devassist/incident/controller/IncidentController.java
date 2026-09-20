package dev.alexey.devassist.incident.controller;

import dev.alexey.devassist.incident.dto.CreateIncidentRequestDTO;
import dev.alexey.devassist.incident.dto.IncidentResponseDTO;
import dev.alexey.devassist.incident.dto.IncidentPageResponseDTO;
import dev.alexey.devassist.incident.dto.UpdateIncidentStatusRequestDTO;
import dev.alexey.devassist.incident.mapper.IncidentMapper;
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
	private final IncidentMapper mapper;

	public IncidentController(IncidentService service, IncidentMapper mapper) {
		this.service = service;
		this.mapper = mapper;
	}

	@Override
	public ResponseEntity<IncidentResponseDTO> create(CreateIncidentRequestDTO request) {
		IncidentResponseDTO response = mapper.toResponse(
				service.create(request.title(), request.description(), request.source()));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(response.id())
				.toUri();
		return ResponseEntity.created(location).body(response);
	}

	@Override
	public IncidentResponseDTO findById(UUID id) {
		return mapper.toResponse(service.findById(id));
	}

	@Override
	public IncidentPageResponseDTO findAll(
			int page,
			int size) {
		return mapper.toPageResponse(service.findAll(page, size));
	}

	@Override
	public IncidentResponseDTO updateStatus(UUID id,
			UpdateIncidentStatusRequestDTO request) {
		return mapper.toResponse(service.updateStatus(id, request.status()));
	}
}
