package dev.alexey.devassist.incident.controller;

import dev.alexey.devassist.incident.entity.Incident;
import dev.alexey.devassist.incident.entity.IncidentSource;
import dev.alexey.devassist.incident.entity.IncidentStatus;
import dev.alexey.devassist.incident.repository.IncidentRepository;

import java.net.URI;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
class IncidentControllerTests {

	private static final String INCIDENTS_URL = "/api/v1/incidents";

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

	@Autowired
	WebApplicationContext context;

	@Autowired
	IncidentRepository repository;

	MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
		repository.deleteAll();
	}

	@ParameterizedTest
	@EnumSource(IncidentSource.class)
	void createsIncidentAndRetrievesItByLocation(IncidentSource source) throws Exception {
		String title = "x".repeat(200);
		MvcResult result = mockMvc.perform(post(INCIDENTS_URL)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"%s","description":"Connection failed","source":"%s"}
						""".formatted(title, source.name())))
				.andExpect(status().isCreated())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(header().exists("Location"))
				.andExpect(jsonPath("$.title").value(title))
				.andExpect(jsonPath("$.description").value("Connection failed"))
				.andExpect(jsonPath("$.status").value("NEW"))
				.andExpect(jsonPath("$.source").value(source.name()))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.updatedAt").isNotEmpty())
				.andReturn();

		String location = result.getResponse().getHeader("Location");
		assertThat(location).startsWith("http://localhost" + INCIDENTS_URL + "/");
		String resourcePath = URI.create(location).getPath();
		UUID id = UUID.fromString(resourcePath.substring(resourcePath.lastIndexOf('/') + 1));
		jsonPath("$.id").value(id.toString()).match(result);

		// No test transaction: this read verifies that the service committed the POST.
		assertThat(repository.count()).isEqualTo(1);
		assertThat(repository.findById(id).orElseThrow())
				.returns(title, Incident::getTitle)
				.returns("Connection failed", Incident::getDescription)
				.returns(IncidentStatus.NEW, Incident::getStatus)
				.returns(source, Incident::getSource);

		mockMvc.perform(get(URI.create(location)))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").value(id.toString()))
				.andExpect(jsonPath("$.title").value(title))
				.andExpect(jsonPath("$.description").value("Connection failed"))
				.andExpect(jsonPath("$.status").value("NEW"))
				.andExpect(jsonPath("$.source").value(source.name()))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.updatedAt").isNotEmpty());
	}

	@Test
	void returnsProblemDetailForUnknownIncident() throws Exception {
		UUID id = UUID.randomUUID();
		mockMvc.perform(get(INCIDENTS_URL + "/{id}", id))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.title").value("Not Found"))
				.andExpect(jsonPath("$.detail").value(containsString(id.toString())))
				.andExpect(jsonPath("$.instance").value(INCIDENTS_URL + "/" + id));
	}

	@Test
	void returnsProblemDetailForMalformedId() throws Exception {
		mockMvc.perform(get(INCIDENTS_URL + "/not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.title").value("Bad Request"))
				.andExpect(jsonPath("$.instance").value(INCIDENTS_URL + "/not-a-uuid"));
	}

	@ParameterizedTest
	@MethodSource("invalidRequests")
	void rejectsInvalidRequestWithProblemDetail(String request) throws Exception {
		mockMvc.perform(post(INCIDENTS_URL)
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.title").value("Bad Request"))
				.andExpect(jsonPath("$.detail").isNotEmpty())
				.andExpect(jsonPath("$.instance").value(INCIDENTS_URL));

		assertThat(repository.count()).isZero();
	}

	static Stream<String> invalidRequests() {
		return Stream.of(
				"""
				{"description":"Description","source":"MANUAL"}
				""",
				"""
				{"title":null,"description":"Description","source":"MANUAL"}
				""",
				"""
				{"title":"","description":"Description","source":"MANUAL"}
				""",
				"""
				{"title":"   ","description":"Description","source":"MANUAL"}
				""",
				"""
				{"title":"%s","description":"Description","source":"MANUAL"}
				""".formatted("x".repeat(201)),
				"""
				{"title":"Incident","source":"MANUAL"}
				""",
				"""
				{"title":"Incident","description":null,"source":"MANUAL"}
				""",
				"""
				{"title":"Incident","description":"","source":"MANUAL"}
				""",
				"""
				{"title":"Incident","description":"   ","source":"MANUAL"}
				""",
				"""
				{"title":"Incident","description":"Description"}
				""",
				"""
				{"title":"Incident","description":"Description","source":null}
				""",
				"""
				{"title":"Incident","description":"Description","source":"UNKNOWN"}
				""",
				"{",
				"");
	}
}
