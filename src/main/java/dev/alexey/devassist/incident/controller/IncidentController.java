package dev.alexey.devassist.incident.controller;

import dev.alexey.devassist.incident.dto.CreateIncidentRequestDTO;
import dev.alexey.devassist.incident.dto.IncidentResponseDTO;
import dev.alexey.devassist.incident.dto.IncidentPageResponseDTO;
import dev.alexey.devassist.incident.dto.UpdateIncidentStatusRequestDTO;
import dev.alexey.devassist.incident.mapper.IncidentMapper;
import dev.alexey.devassist.incident.service.IncidentService;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

	private final IncidentService service;
	private final IncidentMapper mapper;

	public IncidentController(IncidentService service, IncidentMapper mapper) {
		this.service = service;
		this.mapper = mapper;
	}

	@PostMapping
	public ResponseEntity<IncidentResponseDTO> create(@Valid @RequestBody CreateIncidentRequestDTO request) {
		IncidentResponseDTO response = mapper.toResponse(
				service.create(request.title(), request.description(), request.source()));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(response.id())
				.toUri();
		return ResponseEntity.created(location).body(response);
	}

	@GetMapping("/{id}")
	public IncidentResponseDTO findById(@PathVariable UUID id) {
		return mapper.toResponse(service.findById(id));
	}

	@GetMapping
	public IncidentPageResponseDTO findAll(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return mapper.toPageResponse(service.findAll(page, size));
	}

	@PatchMapping("/{id}/status")
	public IncidentResponseDTO updateStatus(@PathVariable UUID id,
			@Valid @RequestBody UpdateIncidentStatusRequestDTO request) {
		return mapper.toResponse(service.updateStatus(id, request.status()));
	}
}
