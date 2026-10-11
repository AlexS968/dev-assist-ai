package dev.alexey.devassist.knowledge.service;

import dev.alexey.devassist.knowledge.dto.CreateKnowledgeBaseRequestDTO;
import dev.alexey.devassist.knowledge.dto.KnowledgeBasePageResponseDTO;
import dev.alexey.devassist.knowledge.dto.KnowledgeBaseResponseDTO;
import dev.alexey.devassist.knowledge.entity.KnowledgeBase;
import dev.alexey.devassist.knowledge.exception.KnowledgeBaseKeyAlreadyExistsException;
import dev.alexey.devassist.knowledge.exception.KnowledgeBaseNotFoundException;
import dev.alexey.devassist.knowledge.mapper.KnowledgeBaseMapper;
import dev.alexey.devassist.knowledge.repository.KnowledgeBaseRepository;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeBaseService {
    private final KnowledgeBaseRepository repository;
    private final KnowledgeBaseMapper mapper;

    public KnowledgeBaseService(KnowledgeBaseRepository repository, KnowledgeBaseMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional
    public KnowledgeBaseResponseDTO create(CreateKnowledgeBaseRequestDTO request) {
        if (repository.existsByKey(request.key())) throw new KnowledgeBaseKeyAlreadyExistsException();
        try {
            return mapper.toResponse(repository.saveAndFlush(new KnowledgeBase(
                    request.key(), request.name(), request.description())));
        } catch (DataIntegrityViolationException exception) {
            throw new KnowledgeBaseKeyAlreadyExistsException();
        }
    }

    @Transactional(readOnly = true)
    public KnowledgeBaseResponseDTO findById(UUID id) {
        return mapper.toResponse(loadRequired(id));
    }

    @Transactional(readOnly = true)
    public KnowledgeBasePageResponseDTO findAll(int page, int size) {
        return mapper.toPageResponse(repository.findAll(PageRequest.of(page, size,
                Sort.by(Sort.Direction.ASC, "name", "id"))));
    }

    @Transactional(readOnly = true)
    public KnowledgeBase requireEntity(UUID id) {
        return loadRequired(id);
    }

    private KnowledgeBase loadRequired(UUID id) {
        return repository.findById(id).orElseThrow(() -> new KnowledgeBaseNotFoundException(id));
    }
}
