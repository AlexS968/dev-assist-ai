package dev.alexey.devassist.incident.mapper;

import dev.alexey.devassist.incident.dto.IncidentPageResponseDTO;
import dev.alexey.devassist.incident.dto.IncidentResponseDTO;
import dev.alexey.devassist.incident.entity.Incident;
import dev.alexey.devassist.incident.enums.IncidentSource;
import dev.alexey.devassist.incident.enums.IncidentStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentMapperTests {

	private final IncidentMapper mapper = Mappers.getMapper(IncidentMapper.class);

	@Test
	void mapsAllIncidentFields() {
		Incident incident = incident("Database unavailable", IncidentSource.MONITORING, IncidentStatus.IN_PROGRESS);

		assertThat(mapper.toResponse(incident))
				.returns(incident.getId(), IncidentResponseDTO::id)
				.returns(incident.getTitle(), IncidentResponseDTO::title)
				.returns(incident.getDescription(), IncidentResponseDTO::description)
				.returns(incident.getStatus(), IncidentResponseDTO::status)
				.returns(incident.getSource(), IncidentResponseDTO::source)
				.returns(incident.getCreatedAt(), IncidentResponseDTO::createdAt)
				.returns(incident.getUpdatedAt(), IncidentResponseDTO::updatedAt);
	}

	@Test
	void mapsPageItemsAndAllPaginationMetadata() {
		Incident first = incident("First", IncidentSource.API, IncidentStatus.RESOLVED);
		Incident second = incident("Second", IncidentSource.MANUAL, IncidentStatus.NEW);
		Page<Incident> page = new PageImpl<>(List.of(first, second), PageRequest.of(2, 2), 9);

		IncidentPageResponseDTO response = mapper.toPageResponse(page);

		assertThat(response)
				.returns(2, IncidentPageResponseDTO::page)
				.returns(2, IncidentPageResponseDTO::size)
				.returns(9L, IncidentPageResponseDTO::totalElements)
				.returns(5, IncidentPageResponseDTO::totalPages);
		assertThat(response.items()).hasSize(2);
		assertThat(response.items().get(0)).usingRecursiveComparison().isEqualTo(first);
		assertThat(response.items().get(1)).usingRecursiveComparison().isEqualTo(second);
	}

	@Test
	void mapsEmptyPage() {
		IncidentPageResponseDTO response = mapper.toPageResponse(Page.empty(PageRequest.of(0, 20)));

		assertThat(response)
				.returns(List.of(), IncidentPageResponseDTO::items)
				.returns(0, IncidentPageResponseDTO::page)
				.returns(20, IncidentPageResponseDTO::size)
				.returns(0L, IncidentPageResponseDTO::totalElements)
				.returns(0, IncidentPageResponseDTO::totalPages);
	}

	private Incident incident(String title, IncidentSource source, IncidentStatus status) {
		Incident incident = new Incident(title, title + " description", source);
		incident.setStatus(status);
		ReflectionTestUtils.setField(incident, "id", UUID.randomUUID());
		ReflectionTestUtils.setField(incident, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
		ReflectionTestUtils.setField(incident, "updatedAt", Instant.parse("2026-01-02T00:00:00Z"));
		return incident;
	}
}
