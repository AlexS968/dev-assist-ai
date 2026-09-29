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
				"$.paths['/api/v1/incidents/{id}/status'].patch",
				"$.paths['/api/v1/incidents/{id}/analysis'].post"
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


		jsonPath(operations[4] + ".responses['404'].content['application/problem+json']").exists().match(result);
		jsonPath(operations[4] + ".responses['502'].content['application/problem+json']").exists().match(result);
		jsonPath(operations[4] + ".responses['504'].content['application/problem+json']").exists().match(result);
		jsonPath(operations[4] + ".responses['200'].content['application/json'].schema['$ref']")
				.value("#/components/schemas/IncidentAnalysisResponseDTO").match(result);
		jsonPath(operations[4] + ".requestBody").doesNotExist().match(result);
		for (String field : new String[]{"provider", "model", "promptVersion", "generatedAt", "latencyMs",
				"inputTokens", "outputTokens", "totalTokens", "attemptCount"}) {
			jsonPath("$.components.schemas.IncidentAnalysisResponseDTO.properties." + field + ".description")
					.isNotEmpty().match(result);
			jsonPath("$.components.schemas.IncidentAnalysisResponseDTO.properties." + field + ".example")
					.exists().match(result);
		}

		assertStructuredSchemas(result);

		for (String schema : new String[]{"CreateIncidentRequestDTO", "IncidentResponseDTO",
				"IncidentPageResponseDTO", "UpdateIncidentStatusRequestDTO", "IncidentAnalysisResponseDTO"}) {
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
	private void assertStructuredSchemas(MvcResult result) throws Exception {
		String schemas = "$.components.schemas.";
		jsonPath(schemas + "IncidentAnalysisResponseDTO.properties.content").doesNotExist().match(result);
		jsonPath(schemas + "IncidentAnalysisResponseDTO.properties.analysis['$ref']")
				.value("#/components/schemas/StructuredIncidentAnalysisDTO").match(result);
		jsonPath(schemas + "StructuredIncidentAnalysisDTO.properties.probableCauses.items['$ref']")
				.value("#/components/schemas/ProbableCauseDTO").match(result);
		jsonPath(schemas + "StructuredIncidentAnalysisDTO.properties.investigationSteps.items['$ref']")
				.value("#/components/schemas/InvestigationStepDTO").match(result);
		jsonPath(schemas + "ProbableCauseDTO.properties.likelihood.enum")
				.value(containsInAnyOrder("LOW", "MEDIUM", "HIGH")).match(result);
		jsonPath(schemas + "ProbableCauseDTO.properties.likelihood.description")
				.value("Qualitative prioritization by the model, not probability or measured confidence").match(result);
		for (String field : new String[]{"summary", "probableCauses", "investigationSteps", "uncertainties"}) {
			jsonPath(schemas + "StructuredIncidentAnalysisDTO.properties." + field + ".description").isNotEmpty().match(result);
		}
		for (String field : new String[]{"title", "explanation", "likelihood", "evidenceToCheck"}) {
			jsonPath(schemas + "ProbableCauseDTO.properties." + field + ".description").isNotEmpty().match(result);
		}
		for (String field : new String[]{"order", "action", "rationale"}) {
			jsonPath(schemas + "InvestigationStepDTO.properties." + field + ".description").isNotEmpty().match(result);
		}
		jsonPath(schemas + "StructuredIncidentAnalysisDTO.properties.summary.maxLength").value(500).match(result);
		jsonPath(schemas + "StructuredIncidentAnalysisDTO.properties.probableCauses.maxItems").value(3).match(result);
		jsonPath(schemas + "StructuredIncidentAnalysisDTO.properties.investigationSteps.maxItems").value(4).match(result);
	}

}
