package dev.alexey.devassist.knowledge.controller;

import dev.alexey.devassist.knowledge.dto.CreateKnowledgeBaseRequestDTO;
import dev.alexey.devassist.knowledge.dto.KnowledgeBasePageResponseDTO;
import dev.alexey.devassist.knowledge.dto.KnowledgeBaseResponseDTO;
import dev.alexey.devassist.knowledge.service.KnowledgeBaseService;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/knowledge-bases")
public class KnowledgeBaseController implements KnowledgeBaseApi {
    private final KnowledgeBaseService service;

    public KnowledgeBaseController(KnowledgeBaseService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<KnowledgeBaseResponseDTO> create(CreateKnowledgeBaseRequestDTO request) {
        KnowledgeBaseResponseDTO response = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @Override
    public KnowledgeBaseResponseDTO findById(UUID id) {
        return service.findById(id);
    }

    @Override
    public KnowledgeBasePageResponseDTO findAll(int page, int size) {
        return service.findAll(page, size);
    }
}
