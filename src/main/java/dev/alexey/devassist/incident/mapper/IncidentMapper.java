package dev.alexey.devassist.incident.mapper;

import dev.alexey.devassist.incident.dto.IncidentResponseDTO;
import dev.alexey.devassist.incident.dto.IncidentPageResponseDTO;
import dev.alexey.devassist.incident.entity.Incident;

import java.util.List;

import org.springframework.data.domain.Page;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IncidentMapper {

	IncidentResponseDTO toResponse(Incident incident);

	List<IncidentResponseDTO> toResponses(List<Incident> incidents);

	@Mapping(source = "content", target = "items", defaultExpression = "java(java.util.List.of())")
	@Mapping(source = "number", target = "page")
	@Mapping(source = "size", target = "size")
	@Mapping(source = "totalElements", target = "totalElements")
	@Mapping(source = "totalPages", target = "totalPages")
	IncidentPageResponseDTO toPageResponse(Page<Incident> incidents);
}
