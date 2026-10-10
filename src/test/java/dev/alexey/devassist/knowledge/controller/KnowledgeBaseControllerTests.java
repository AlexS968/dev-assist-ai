package dev.alexey.devassist.knowledge.controller;

import dev.alexey.devassist.knowledge.entity.KnowledgeBase;
import dev.alexey.devassist.knowledge.repository.KnowledgeBaseRepository;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {"app.ai.provider=ollama", "app.rag.enabled=false", "spring.ai.openai.api-key="})
class KnowledgeBaseControllerTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            org.testcontainers.utility.DockerImageName.parse("pgvector/pgvector:0.8.6-pg17-bookworm")
                    .asCompatibleSubstituteFor("postgres"));
    @Autowired WebApplicationContext context;
    @Autowired KnowledgeBaseRepository bases;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        bases.deleteAll();
    }

    @Test void createsAndGetsEmptyBaseWithLocation() throws Exception {
        var result = mvc.perform(post("/api/v1/knowledge-bases").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"key":"  checkout-service  ","name":"  Checkout Service  ","description":"Docs"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("checkout-service"))
                .andExpect(jsonPath("$.name").value("Checkout Service"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.documents").doesNotExist())
                .andExpect(jsonPath("$.chunks").doesNotExist())
                .andExpect(jsonPath("$.embeddings").doesNotExist())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        UUID id = UUID.fromString(URI.create(location).getPath().replace("/api/v1/knowledge-bases/", ""));
        assertThat(bases.findById(id)).isPresent();
        mvc.perform(get(URI.create(location))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test void listUsesStableNameAndIdOrderAndPagination() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        KnowledgeBase second = bases.saveAndFlush(new KnowledgeBase("z-" + suffix, "Z " + suffix, null));
        KnowledgeBase first = bases.saveAndFlush(new KnowledgeBase("a-" + suffix, "A " + suffix, null));
        KnowledgeBase tied = bases.saveAndFlush(new KnowledgeBase("a2-" + suffix, "A " + suffix, null));
        var sorted = java.util.stream.Stream.of(first.getId(), tied.getId())
                .sorted(java.util.Comparator.comparing(UUID::toString)).toList();
        mvc.perform(get("/api/v1/knowledge-bases").param("page", "0").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.items[0].id").value(sorted.get(0).toString()))
                .andExpect(jsonPath("$.items[1].id").value(sorted.get(1).toString()))
                .andExpect(jsonPath("$.items[2].id").value(second.getId().toString()));
        mvc.perform(get("/api/v1/knowledge-bases").param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test void invalidAndDuplicateKeysReturnSafeProblems() throws Exception {
        String path = "/api/v1/knowledge-bases";
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"Bad_Key\",\"name\":\"Name\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\":\"valid-key\",\"name\":\"  \"}"))
                .andExpect(status().isBadRequest());
        String body = "{\"key\":\"duplicate-key\",\"name\":\"Name\"}";
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("KnowledgeBase key already exists."));
    }

    @Test void lengthLimitsAndBoundaryHyphensAreRejected() throws Exception {
        String path = "/api/v1/knowledge-bases";
        for (String body : new String[]{
                "{\"key\":\"-bad\",\"name\":\"Name\"}",
                "{\"key\":\"bad-\",\"name\":\"Name\"}",
                "{\"key\":\"" + "a".repeat(101) + "\",\"name\":\"Name\"}",
                "{\"key\":\"valid\",\"name\":\"" + "n".repeat(256) + "\"}",
                "{\"key\":\"valid\",\"name\":\"Name\",\"description\":\""
                        + "d".repeat(5001) + "\"}"}) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(bases.count()).isZero();
    }

    @Test void unknownBaseReturns404() throws Exception {
        mvc.perform(get("/api/v1/knowledge-bases/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("KnowledgeBase not found."));
    }
}
