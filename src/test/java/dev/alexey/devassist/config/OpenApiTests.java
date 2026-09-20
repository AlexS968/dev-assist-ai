package dev.alexey.devassist.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
class OpenApiTests {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

	@Autowired
	WebApplicationContext context;

	@Test
	void documentsIncidentOperationsAndSchemas() throws Exception {
		MvcResult result = MockMvcBuilders.webAppContextSetup(context).build()
				.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.info.title").value("Dev Assist AI API"))
				.andExpect(jsonPath("$.info.version").value("v1"))
				.andExpect(jsonPath("$.info.description").value("REST API for managing and analyzing software incidents"))
				.andReturn();

		String[] operations = {
				"$.paths['/api/v1/incidents'].post",
				"$.paths['/api/v1/incidents/{id}'].get",
				"$.paths['/api/v1/incidents'].get",
				"$.paths['/api/v1/incidents/{id}/status'].patch"
		};
		for (String operation : operations) {
			jsonPath(operation + ".summary").isNotEmpty().match(result);
			jsonPath(operation + ".responses['400'].content['application/problem+json']").exists().match(result);
		}
		jsonPath(operations[0] + ".responses['201']").exists().match(result);
		for (int index = 1; index < operations.length; index++) {
			jsonPath(operations[index] + ".responses['200']").exists().match(result);
		}
		jsonPath(operations[1] + ".responses['404']").exists().match(result);
		jsonPath(operations[3] + ".responses['404']").exists().match(result);
		jsonPath(operations[3] + ".responses['409']").exists().match(result);

		for (String schema : new String[]{"CreateIncidentRequestDTO", "IncidentResponseDTO",
				"IncidentPageResponseDTO", "UpdateIncidentStatusRequestDTO"}) {
			jsonPath("$.components.schemas." + schema).exists().match(result);
		}
		jsonPath("$.components.schemas.IncidentPageResponseDTO.properties.items.items['$ref']")
				.value("#/components/schemas/IncidentResponseDTO").match(result);

		for (String schema : new String[]{"CreateIncidentRequestDTO", "IncidentResponseDTO"}) {
			jsonPath("$.components.schemas." + schema + ".properties.source.enum")
					.value(containsInAnyOrder("MANUAL", "API", "MONITORING")).match(result);
		}
		for (String schema : new String[]{"UpdateIncidentStatusRequestDTO", "IncidentResponseDTO"}) {
			jsonPath("$.components.schemas." + schema + ".properties.status.enum")
					.value(containsInAnyOrder("NEW", "IN_PROGRESS", "RESOLVED")).match(result);
		}
	}
}
