package dev.alexey.devassist.incident.service;

import dev.alexey.devassist.analysis.IncidentAnalysisGateway;
import dev.alexey.devassist.analysis.IncidentAnalysisInput;
import dev.alexey.devassist.analysis.IncidentAnalysisResult;
import dev.alexey.devassist.incident.entity.Incident;
import dev.alexey.devassist.incident.enums.IncidentSource;
import dev.alexey.devassist.incident.exception.IncidentNotFoundException;
import dev.alexey.devassist.incident.mapper.IncidentMapper;
import dev.alexey.devassist.incident.repository.IncidentRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentAnalysisServiceTests {

	private final IncidentRepository repository = mock(IncidentRepository.class);
	private final IncidentAnalysisGateway gateway = mock(IncidentAnalysisGateway.class);
	private final IncidentAnalysisService service = new IncidentAnalysisService(repository, gateway,
			Mappers.getMapper(IncidentMapper.class));

	@Test
	void loadsIncidentPassesOnlyTitleAndDescriptionAndMapsAllResultFields() {
		UUID id = UUID.randomUUID();
		var incident = new Incident("Title", "Description", IncidentSource.MONITORING);
		when(repository.findById(id)).thenReturn(Optional.of(incident));
		var input = new IncidentAnalysisInput("Title", "Description");
		var result = new IncidentAnalysisResult("Analysis", "fake", "test-model",
				Instant.parse("2026-09-27T12:00:00Z"), 125, 12, 8, 20);
		when(gateway.analyze(input)).thenReturn(result);

		assertThat(service.analyze(id)).usingRecursiveComparison().isEqualTo(result);

		var order = inOrder(repository, gateway);
		order.verify(repository).findById(id);
		order.verify(gateway).analyze(input);
		verifyNoMoreInteractions(repository, gateway);
	}

	@Test
	void unknownIncidentDoesNotCallGateway() {
		UUID id = UUID.randomUUID();
		when(repository.findById(id)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.analyze(id)).isInstanceOf(IncidentNotFoundException.class)
				.hasMessage("Incident with id " + id + " was not found.");
		verifyNoInteractions(gateway);
	}
}
