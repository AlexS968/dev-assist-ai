package dev.alexey.devassist.incident.controller;

import dev.alexey.devassist.incident.dto.CreateIncidentRequestDTO;
import dev.alexey.devassist.incident.dto.IncidentResponseDTO;
import dev.alexey.devassist.incident.mapper.IncidentMapper;
import dev.alexey.devassist.incident.service.IncidentService;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

	private final IncidentService service;

	public IncidentController(IncidentService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<IncidentResponseDTO> create(@Valid @RequestBody CreateIncidentRequestDTO request) {
		IncidentResponseDTO response = IncidentMapper.toResponse(
				service.create(request.title(), request.description(), request.source()));
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(response.id())
				.toUri();
		return ResponseEntity.created(location).body(response);
	}

	@GetMapping("/{id}")
	public IncidentResponseDTO findById(@PathVariable UUID id) {
		return IncidentMapper.toResponse(service.findById(id));
	}
}
