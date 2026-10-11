package dev.alexey.devassist.knowledge.mapper;

import dev.alexey.devassist.knowledge.dto.KnowledgeBasePageResponseDTO;
import dev.alexey.devassist.knowledge.dto.KnowledgeBaseResponseDTO;
import dev.alexey.devassist.knowledge.entity.KnowledgeBase;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import org.springframework.data.domain.Page;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface KnowledgeBaseMapper {
    KnowledgeBaseResponseDTO toResponse(KnowledgeBase base);
    List<KnowledgeBaseResponseDTO> toResponses(List<KnowledgeBase> bases);

    @Mapping(source = "content", target = "items", defaultExpression = "java(java.util.List.of())")
    @Mapping(source = "number", target = "page")
    KnowledgeBasePageResponseDTO toPageResponse(Page<KnowledgeBase> bases);
}
