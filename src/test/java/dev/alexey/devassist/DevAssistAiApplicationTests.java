package dev.alexey.devassist;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class DevAssistAiApplicationTests {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void flywayCreatesIncidentsTable() {
		Integer tableCount = jdbcTemplate.queryForObject(
				"""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'incidents'
                """,
				Integer.class
		);

		assertThat(tableCount).isEqualTo(1);
	}

	@Test
	void flywayRegistersSuccessfulMigration() {
		Integer migrationCount = jdbcTemplate.queryForObject(
				"""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '1'
                  AND success = true
                """,
				Integer.class
		);

		assertThat(migrationCount).isEqualTo(1);
	}
}
