package dev.alexey.devassist.incident.repository;

import dev.alexey.devassist.incident.entity.Incident;
import dev.alexey.devassist.incident.entity.IncidentSource;
import dev.alexey.devassist.incident.entity.IncidentStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
@Transactional
class IncidentRepositoryTests {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

	@Autowired
	IncidentRepository repository;

	@Autowired
	EntityManager entityManager;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void savesAndLoadsAllFields() {
		String title = "x".repeat(200);
		String description = "Ошибка соединения\n".repeat(1000);
		Incident incident = new Incident(title, description, IncidentSource.MANUAL);
		Instant beforeSave = Instant.now().truncatedTo(ChronoUnit.SECONDS);

		repository.saveAndFlush(incident);
		entityManager.clear();

		assertThat(incident.getId()).isNotNull();
		Incident loaded = repository.findById(incident.getId()).orElseThrow();
		assertThat(loaded.getTitle()).isEqualTo(title);
		assertThat(loaded.getDescription()).isEqualTo(description);
		assertThat(loaded.getStatus()).isEqualTo(IncidentStatus.NEW);
		assertThat(loaded.getSource()).isEqualTo(IncidentSource.MANUAL);
		assertThat(loaded.getCreatedAt()).isBetween(beforeSave, Instant.now());
		assertThat(loaded.getUpdatedAt()).isEqualTo(loaded.getCreatedAt());
	}

	@Test
	void updatesMutableFieldsAndPreservesCreationTime() {
		Incident incident = repository.saveAndFlush(
				new Incident("Connection failed", "Initial description", IncidentSource.MANUAL));
		Instant originalTime = Instant.parse("2020-01-01T00:00:00Z");
		jdbcTemplate.update(
				"UPDATE incidents SET created_at = ?, updated_at = ? WHERE id = ?",
				java.sql.Timestamp.from(originalTime), java.sql.Timestamp.from(originalTime), incident.getId());
		entityManager.clear();

		Incident loaded = repository.findById(incident.getId()).orElseThrow();
		loaded.setTitle("Connection restored");
		loaded.setDescription("Updated description");
		loaded.setStatus(IncidentStatus.RESOLVED);
		loaded.setSource(IncidentSource.API);
		Instant beforeUpdate = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		repository.saveAndFlush(loaded);
		entityManager.clear();

		Incident updated = repository.findById(incident.getId()).orElseThrow();
		assertThat(updated)
				.returns("Connection restored", Incident::getTitle)
				.returns("Updated description", Incident::getDescription)
				.returns(IncidentStatus.RESOLVED, Incident::getStatus)
				.returns(IncidentSource.API, Incident::getSource)
				.returns(originalTime, Incident::getCreatedAt)
				.satisfies(incidentAfterUpdate -> assertThat(incidentAfterUpdate.getUpdatedAt())
						.isBetween(beforeUpdate, Instant.now()));
	}

	@ParameterizedTest
	@EnumSource(IncidentStatus.class)
	void storesStatusAsString(IncidentStatus status) {
		Incident incident = new Incident("Incident", "Description", IncidentSource.MANUAL);
		incident.setStatus(status);
		repository.saveAndFlush(incident);
		entityManager.clear();

		assertThat(jdbcTemplate.queryForObject("SELECT status FROM incidents WHERE id = ?",
				String.class, incident.getId())).isEqualTo(status.name());
		assertThat(repository.findById(incident.getId()).orElseThrow().getStatus()).isEqualTo(status);
	}

	@ParameterizedTest
	@EnumSource(IncidentSource.class)
	void storesSourceAsString(IncidentSource source) {
		Incident incident = repository.saveAndFlush(new Incident("Incident", "Description", source));
		entityManager.clear();

		assertThat(jdbcTemplate.queryForObject("SELECT source FROM incidents WHERE id = ?",
				String.class, incident.getId())).isEqualTo(source.name());
		assertThat(repository.findById(incident.getId()).orElseThrow().getSource()).isEqualTo(source);
	}

	@Test
	void deletesIncident() {
		Incident incident = repository.saveAndFlush(
				new Incident("Incident", "Description", IncidentSource.MANUAL));
		entityManager.clear();

		repository.deleteById(incident.getId());
		repository.flush();
		entityManager.clear();

		assertThat(repository.findById(incident.getId())).isEmpty();
	}

	@Test
	void returnsEmptyForUnknownId() {
		assertThat(repository.findById(UUID.randomUUID())).isEmpty();
	}

	@Test
	void rejectsTitleLongerThanDatabaseLimit() {
		Incident incident = new Incident("x".repeat(201), "Description", IncidentSource.MANUAL);

		assertThatThrownBy(() -> repository.saveAndFlush(incident))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
