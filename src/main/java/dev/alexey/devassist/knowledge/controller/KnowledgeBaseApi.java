package dev.alexey.devassist.knowledge.controller;

import dev.alexey.devassist.knowledge.dto.CreateKnowledgeBaseRequestDTO;
import dev.alexey.devassist.knowledge.dto.KnowledgeBasePageResponseDTO;
import dev.alexey.devassist.knowledge.dto.KnowledgeBaseResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "KnowledgeBases", description = "Create and list empty project or service knowledge bases")
@ApiResponse(responseCode = "400", description = "Validation failed or request is malformed",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public interface KnowledgeBaseApi {
    @PostMapping
    @Operation(summary = "Create an empty KnowledgeBase")
    @ApiResponse(responseCode = "201", description = "KnowledgeBase created",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = KnowledgeBaseResponseDTO.class)))
    @ApiResponse(responseCode = "409", description = "KnowledgeBase key already exists",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    ResponseEntity<KnowledgeBaseResponseDTO> create(@Valid @RequestBody CreateKnowledgeBaseRequestDTO request);

    @GetMapping("/{id}")
    @Operation(summary = "Get a KnowledgeBase by ID")
    @ApiResponse(responseCode = "200", description = "KnowledgeBase found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = KnowledgeBaseResponseDTO.class)))
    @ApiResponse(responseCode = "404", description = "KnowledgeBase does not exist",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    KnowledgeBaseResponseDTO findById(@Parameter(description = "KnowledgeBase UUID") @PathVariable UUID id);

    @GetMapping
    @Operation(summary = "List KnowledgeBases", description = "Ordered by name ASC, then id ASC.")
    @ApiResponse(responseCode = "200", description = "Page of KnowledgeBases",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = KnowledgeBasePageResponseDTO.class)))
    KnowledgeBasePageResponseDTO findAll(
            @Parameter(description = "Zero-based page index", schema = @Schema(minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size from 1 to 100", schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size);
}
