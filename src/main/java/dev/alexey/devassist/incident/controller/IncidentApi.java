package dev.alexey.devassist.incident.controller;

import dev.alexey.devassist.incident.dto.CreateIncidentRequestDTO;
import dev.alexey.devassist.incident.dto.IncidentPageResponseDTO;
import dev.alexey.devassist.incident.dto.IncidentResponseDTO;
import dev.alexey.devassist.incident.dto.UpdateIncidentStatusRequestDTO;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Incidents", description = "Create, retrieve and manage incident status")
@ApiResponse(responseCode = "400", description = "Validation failed or request is malformed",
		content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public interface IncidentApi {

	@PostMapping
	@Operation(summary = "Create an incident", description = "Creates an incident with status NEW and returns its URI in Location.")
	@ApiResponse(responseCode = "201", description = "Incident created",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = IncidentResponseDTO.class)))
	ResponseEntity<IncidentResponseDTO> create(@Valid @RequestBody CreateIncidentRequestDTO request);

	@GetMapping("/{id}")
	@Operation(summary = "Get an incident by ID")
	@ApiResponse(responseCode = "200", description = "Incident found",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = IncidentResponseDTO.class)))
	@ApiResponse(responseCode = "404", description = "Incident does not exist",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	IncidentResponseDTO findById(@Parameter(description = "Incident UUID") @PathVariable UUID id);

	@GetMapping
	@Operation(summary = "List incidents", description = "Returns a page ordered by createdAt DESC, then id DESC.")
	@ApiResponse(responseCode = "200", description = "Incident page; items is empty when no incidents match",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = IncidentPageResponseDTO.class)))
	IncidentPageResponseDTO findAll(
			@Parameter(description = "Zero-based page index", example = "0",
					schema = @Schema(minimum = "0", defaultValue = "0"))
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@Parameter(description = "Number of incidents per page, from 1 to 100", example = "20",
					schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size);

	@PatchMapping("/{id}/status")
	@Operation(summary = "Change incident status", description = "Only NEW -> IN_PROGRESS and IN_PROGRESS -> RESOLVED are allowed.")
	@ApiResponse(responseCode = "200", description = "Status updated",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = IncidentResponseDTO.class)))
	@ApiResponse(responseCode = "404", description = "Incident does not exist",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Requested status transition is not allowed",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	IncidentResponseDTO updateStatus(@Parameter(description = "Incident UUID") @PathVariable UUID id,
			@Valid @RequestBody UpdateIncidentStatusRequestDTO request);
}
