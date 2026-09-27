package dev.alexey.devassist.incident.controller;

import dev.alexey.devassist.analysis.IncidentAnalysisGateway;
import dev.alexey.devassist.analysis.IncidentAnalysisInput;
import dev.alexey.devassist.analysis.IncidentAnalysisResult;
import dev.alexey.devassist.analysis.exception.IncidentAnalysisException;
import dev.alexey.devassist.analysis.exception.IncidentAnalysisTimeoutException;
import dev.alexey.devassist.analysis.exception.EmptyIncidentAnalysisException;
import dev.alexey.devassist.incident.service.IncidentAnalysisService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.hamcrest.Matchers.nullValue;

import dev.alexey.devassist.incident.entity.Incident;
import dev.alexey.devassist.incident.enums.IncidentSource;
import dev.alexey.devassist.incident.enums.IncidentStatus;
import dev.alexey.devassist.incident.repository.IncidentRepository;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

	@Autowired
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	IncidentAnalysisGateway gateway;

	@Autowired
	IncidentAnalysisService analysisService;

	@Autowired
	PlatformTransactionManager transactionManager;

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

	@Test
	void returnsEmptyPageWithDefaults() throws Exception {
		mockMvc.perform(get(INCIDENTS_URL))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.items").isEmpty())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.totalPages").value(0));
	}

	@Test
	void listsNewestFirstWithPaginationMetadata() throws Exception {
		Incident oldest = createIncidentAt("Oldest", "2026-01-01T00:00:00Z");
		Incident newest = createIncidentAt("Newest", "2026-01-03T00:00:00Z");
		Incident middle = createIncidentAt("Middle", "2026-01-02T00:00:00Z");

		mockMvc.perform(get(INCIDENTS_URL).param("page", "0").param("size", "2")
				.param("sort", "createdAt,asc"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(2))
				.andExpect(jsonPath("$.items[0].id").value(newest.getId().toString()))
				.andExpect(jsonPath("$.items[0].title").value("Newest"))
				.andExpect(jsonPath("$.items[0].description").value("Description"))
				.andExpect(jsonPath("$.items[0].source").value("MANUAL"))
				.andExpect(jsonPath("$.items[0].status").value("NEW"))
				.andExpect(jsonPath("$.items[0].createdAt").value("2026-01-03T00:00:00Z"))
				.andExpect(jsonPath("$.items[0].updatedAt").isNotEmpty())
				.andExpect(jsonPath("$.items[1].id").value(middle.getId().toString()))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(2));

		mockMvc.perform(get(INCIDENTS_URL).param("page", "1").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].id").value(oldest.getId().toString()))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(2));

		mockMvc.perform(get(INCIDENTS_URL).param("page", "2").param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty())
				.andExpect(jsonPath("$.page").value(2))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(2));
	}

	@Test
	void pagesWithIdenticalCreationTimeAreStableAndDoNotOverlap() throws Exception {
		List<String> idsDescending = List.of(
				"ffffffff-ffff-ffff-ffff-ffffffffffff",
				"80000000-0000-0000-0000-000000000000",
				"70000000-0000-0000-0000-000000000000",
				"10000000-0000-0000-0000-000000000000",
				"00000000-0000-0000-0000-000000000001");
		Timestamp createdAt = Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"));
		// Insert out of UUID order so that insertion order cannot satisfy the assertions.
		for (int index : new int[]{2, 4, 0, 3, 1}) {
			jdbcTemplate.update("""
					INSERT INTO incidents (id, title, description, status, source, created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					""", UUID.fromString(idsDescending.get(index)), "Incident", "Description",
					"NEW", "MANUAL", createdAt, createdAt);
		}

		for (int attempt = 0; attempt < 2; attempt++) {
			for (int page = 0; page < 3; page++) {
				List<String> expectedIds = idsDescending.subList(page * 2, Math.min(page * 2 + 2, 5));
				MvcResult result = mockMvc.perform(get(INCIDENTS_URL).param("page", Integer.toString(page)).param("size", "2"))
						.andExpect(status().isOk())
						.andExpect(jsonPath("$.items.length()").value(expectedIds.size()))
						.andExpect(jsonPath("$.page").value(page))
						.andExpect(jsonPath("$.size").value(2))
						.andExpect(jsonPath("$.totalElements").value(5))
						.andExpect(jsonPath("$.totalPages").value(3))
						.andReturn();
				for (int item = 0; item < expectedIds.size(); item++) {
					jsonPath("$.items[" + item + "].id").value(expectedIds.get(item)).match(result);
				}
			}
		}
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 100})
	void acceptsPageSizeBoundaries(int size) throws Exception {
		mockMvc.perform(get(INCIDENTS_URL).param("size", Integer.toString(size)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(size));
	}

	@ParameterizedTest
	@CsvSource({"-1,20", "0,0", "0,-1", "0,101", "abc,20", "0,abc"})
	void rejectsInvalidPagination(String page, String size) throws Exception {
		mockMvc.perform(get(INCIDENTS_URL).param("page", page).param("size", size))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));
	}

	@Test
	void progressesIncidentThroughAllowedStatusesAndPersistsChanges() throws Exception {
		Incident incident = createIncidentAt("Incident", "2026-01-01T00:00:00Z");
		for (IncidentStatus next : new IncidentStatus[]{IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED}) {
			MvcResult response = mockMvc.perform(patch(INCIDENTS_URL + "/{id}/status", incident.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"" + next.name() + "\"}"))
					.andExpect(status().isOk())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
					.andExpect(jsonPath("$.id").value(incident.getId().toString()))
					.andExpect(jsonPath("$.status").value(next.name()))
					.andExpect(jsonPath("$.title").value("Incident"))
					.andExpect(jsonPath("$.description").value("Description"))
					.andExpect(jsonPath("$.source").value("MANUAL"))
					.andExpect(jsonPath("$.createdAt").value("2026-01-01T00:00:00Z"))
					.andExpect(jsonPath("$.updatedAt").isNotEmpty())
					.andReturn();

			var persisted = repository.findById(incident.getId()).orElseThrow();
			assertThat(persisted.getStatus()).isEqualTo(next);
			String responseTimestamp = com.jayway.jsonpath.JsonPath.read(
					response.getResponse().getContentAsString(), "$.updatedAt");
			assertThat(Instant.parse(responseTimestamp).truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
					.isEqualTo(persisted.getUpdatedAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
		}
	}

	@ParameterizedTest
	@CsvSource({
			"NEW,NEW", "NEW,RESOLVED",
			"IN_PROGRESS,NEW", "IN_PROGRESS,IN_PROGRESS",
			"RESOLVED,NEW", "RESOLVED,IN_PROGRESS", "RESOLVED,RESOLVED"
	})
	void rejectsForbiddenStatusTransitions(IncidentStatus current, IncidentStatus requested) throws Exception {
		Incident incident = new Incident("Incident", "Description", IncidentSource.MANUAL);
		incident.setStatus(current);
		repository.saveAndFlush(incident);
		Instant updatedAt = repository.findById(incident.getId()).orElseThrow().getUpdatedAt();

		mockMvc.perform(patch(INCIDENTS_URL + "/{id}/status", incident.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"" + requested.name() + "\"}"))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.detail").value(containsString("from " + current + " to " + requested)))
				.andExpect(jsonPath("$.instance").value(INCIDENTS_URL + "/" + incident.getId() + "/status"));

		assertThat(repository.findById(incident.getId()).orElseThrow())
				.returns(current, Incident::getStatus)
				.returns(updatedAt, Incident::getUpdatedAt);
	}

	@Test
	void returnsNotFoundForStatusUpdateOfUnknownIncident() throws Exception {
		UUID id = UUID.randomUUID();
		mockMvc.perform(patch(INCIDENTS_URL + "/{id}/status", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"IN_PROGRESS\"}"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.detail").value(containsString(id.toString())));
	}

	@ParameterizedTest
	@ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\"UNKNOWN\"}", "{"})
	void rejectsInvalidStatusRequest(String request) throws Exception {
		Incident incident = repository.saveAndFlush(new Incident("Incident", "Description", IncidentSource.MANUAL));
		mockMvc.perform(patch(INCIDENTS_URL + "/{id}/status", incident.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400));

		assertThat(repository.findById(incident.getId()).orElseThrow().getStatus()).isEqualTo(IncidentStatus.NEW);
	}

	private Incident createIncidentAt(String title, String createdAt) {
		Incident incident = repository.saveAndFlush(new Incident(title, "Description", IncidentSource.MANUAL));
		jdbcTemplate.update("UPDATE incidents SET created_at = ? WHERE id = ?",
				Timestamp.from(Instant.parse(createdAt)), incident.getId());
		return incident;
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

	@Test
	void analyzesExistingIncidentOutsideTransactionWithoutChangingDatabase() throws Exception {
		var incident = repository.save(new Incident("Database unavailable", "Connection failed", IncidentSource.API));
		var generatedAt = Instant.parse("2026-09-27T12:00:00Z");
		when(gateway.analyze(new IncidentAnalysisInput("Database unavailable", "Connection failed")))
				.thenAnswer(invocation -> {
					assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
					return new IncidentAnalysisResult("Check connectivity", "fake", "test-model", "incident-analysis-v1", generatedAt,
							125, 12, 8, 20);
				});
		mockMvc.perform(post(INCIDENTS_URL + "/{id}/analysis", incident.getId()))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.content").value("Check connectivity"))
				.andExpect(jsonPath("$.provider").value("fake"))
				.andExpect(jsonPath("$.model").value("test-model"))
				.andExpect(jsonPath("$.promptVersion").value("incident-analysis-v1"))
				.andExpect(jsonPath("$.generatedAt").value(generatedAt.toString()))
				.andExpect(jsonPath("$.latencyMs").value(125))
				.andExpect(jsonPath("$.inputTokens").value(12))
				.andExpect(jsonPath("$.outputTokens").value(8))
				.andExpect(jsonPath("$.totalTokens").value(20));
		verify(gateway).analyze(new IncidentAnalysisInput("Database unavailable", "Connection failed"));
		assertThat(repository.count()).isEqualTo(1);
		assertThat(repository.findById(incident.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(incident);
	}

	@Test
	void analysisPreservesMissingUsageAsJsonNull() throws Exception {
		var incident = repository.save(new Incident("Title", "Description", IncidentSource.API));
		when(gateway.analyze(any())).thenReturn(new IncidentAnalysisResult("Analysis", "fake", "model", "incident-analysis-v1",
				Instant.parse("2026-09-27T12:00:00Z"), 1, null, null, null));
		mockMvc.perform(post(INCIDENTS_URL + "/{id}/analysis", incident.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.inputTokens").value(nullValue()))
				.andExpect(jsonPath("$.outputTokens").value(nullValue()))
				.andExpect(jsonPath("$.totalTokens").value(nullValue()));
	}

	@Test
	void analysisOfUnknownIncidentReturnsExisting404() throws Exception {
		UUID id = UUID.randomUUID();
		mockMvc.perform(post(INCIDENTS_URL + "/{id}/analysis", id))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.title").value("Not Found"))
				.andExpect(jsonPath("$.detail").value("Incident with id " + id + " was not found."))
				.andExpect(jsonPath("$.instance").value(INCIDENTS_URL + "/" + id + "/analysis"));
		verifyNoInteractions(gateway);
	}

	@Test
	void providerFailureReturnsSanitized502() throws Exception {
		var incident = repository.save(new Incident("Title", "private prompt", IncidentSource.API));
		when(gateway.analyze(any())).thenThrow(new IncidentAnalysisException(
				new IllegalStateException("fake-api-key private prompt provider internals")));
		var result = mockMvc.perform(post(INCIDENTS_URL + "/{id}/analysis", incident.getId()))
				.andExpect(status().isBadGateway())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(502))
				.andExpect(jsonPath("$.title").value("Bad Gateway"))
				.andExpect(jsonPath("$.detail").value("Incident analysis is temporarily unavailable."))
				.andExpect(jsonPath("$.instance").value(INCIDENTS_URL + "/" + incident.getId() + "/analysis"))
				.andReturn();
		assertThat(result.getResponse().getContentAsString())
				.doesNotContain("fake-api-key", "private prompt", "provider internals", "IllegalStateException");
	}

	@Test
	void emptyProviderContentReturns502() throws Exception {
		var incident = repository.save(new Incident("Title", "Description", IncidentSource.API));
		when(gateway.analyze(any())).thenThrow(new EmptyIncidentAnalysisException());
		mockMvc.perform(post(INCIDENTS_URL + "/{id}/analysis", incident.getId()))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.detail").value("Incident analysis is temporarily unavailable."));
	}

	@Test
	void suspendsAndRestoresCallerTransactionAroundAnalysis() {
		var incident = repository.save(new Incident("Title", "Description", IncidentSource.API));
		when(gateway.analyze(any())).thenAnswer(invocation -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			return new IncidentAnalysisResult("Analysis", "fake", "model", "incident-analysis-v1", Instant.EPOCH, 1, null, null, null);
		});
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
			analysisService.analyze(incident.getId());
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
		});
		verify(gateway).analyze(new IncidentAnalysisInput("Title", "Description"));
	}

	@Test
	void providerTimeoutReturnsSanitized504() throws Exception {
		var incident = repository.save(new Incident("Title", "private incident description", IncidentSource.API));
		when(gateway.analyze(any())).thenThrow(new IncidentAnalysisTimeoutException(
				new IllegalStateException("fake-api-key full prompt private incident description https://provider.invalid/private")));
		var result = mockMvc.perform(post(INCIDENTS_URL + "/{id}/analysis", incident.getId()))
				.andExpect(status().isGatewayTimeout())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(504))
				.andExpect(jsonPath("$.title").value("Gateway Timeout"))
				.andExpect(jsonPath("$.detail").value("Incident analysis timed out. Please try again later."))
				.andExpect(jsonPath("$.instance").value(INCIDENTS_URL + "/" + incident.getId() + "/analysis"))
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain(
				"fake-api-key", "full prompt", "private incident description", "provider.invalid", "IllegalStateException", "stackTrace");
	}
}
