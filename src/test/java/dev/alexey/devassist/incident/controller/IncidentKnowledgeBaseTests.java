package dev.alexey.devassist.incident.controller;

import dev.alexey.devassist.incident.entity.Incident;
import dev.alexey.devassist.incident.enums.IncidentSource;
import dev.alexey.devassist.incident.repository.IncidentRepository;
import dev.alexey.devassist.incident.mapper.IncidentMapper;
import dev.alexey.devassist.knowledge.entity.KnowledgeBase;
import dev.alexey.devassist.knowledge.entity.KnowledgeChunk;
import dev.alexey.devassist.knowledge.entity.KnowledgeDocument;
import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import dev.alexey.devassist.knowledge.repository.KnowledgeBaseRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeChunkRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeDocumentRepository;
import dev.alexey.devassist.knowledge.retrieval.KnowledgeRetrievalService;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(properties = {"app.ai.provider=ollama", "app.rag.enabled=false", "spring.ai.openai.api-key="})
class IncidentKnowledgeBaseTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            org.testcontainers.utility.DockerImageName.parse("pgvector/pgvector:0.8.6-pg17-bookworm")
                    .asCompatibleSubstituteFor("postgres"));
    @Autowired WebApplicationContext context;
    @Autowired IncidentRepository incidents;
    @Autowired KnowledgeBaseRepository bases;
    @Autowired KnowledgeDocumentRepository documents;
    @Autowired KnowledgeChunkRepository chunks;
    @Autowired EntityManager entityManager;
    @Autowired IncidentMapper mapper;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        incidents.deleteAll();
        bases.deleteAll();
    }

    private KnowledgeBase base(String key) {
        return bases.saveAndFlush(new KnowledgeBase(key, key, null));
    }

    private Incident incident() {
        return incidents.saveAndFlush(new Incident("Title", "Description", IncidentSource.MANUAL));
    }

    private String createJson(UUID baseId) {
        return "{\"title\":\"Title\",\"description\":\"Description\",\"source\":\"MANUAL\",\"knowledgeBaseId\":\""
                + baseId + "\"}";
    }

    @Test void oldAndNewCreateContractsRemainCompatible() throws Exception {
        assertThat(context.getBeansOfType(KnowledgeRetrievalService.class)).isEmpty();
        KnowledgeBase base = base("project-one");
        mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Legacy\",\"description\":\"Text\",\"source\":\"MANUAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.knowledgeBaseId").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON)
                .content(createJson(base.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.knowledgeBaseId").value(base.getId().toString()))
                .andExpect(jsonPath("$.knowledgeBase").doesNotExist());
        assertThat(incidents.count()).isEqualTo(2);
    }

    @Test void unknownBaseRejectsCreationWithoutSavingIncident() throws Exception {
        mvc.perform(post("/api/v1/incidents").contentType(MediaType.APPLICATION_JSON)
                .content(createJson(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("KnowledgeBase not found."));
        assertThat(incidents.count()).isZero();
    }

    @Test void patchAssignsReplacesClearsAndPreservesNoOpTimestamp() throws Exception {
        Incident incident = incident();
        KnowledgeBase first = base("first-service");
        KnowledgeBase second = base("second-service");
        Instant originalCreated = incidents.findById(incident.getId()).orElseThrow().getCreatedAt();
        patchBase(incident.getId(), first.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.knowledgeBaseId").value(first.getId().toString()));
        Instant assignedAt = incidents.findById(incident.getId()).orElseThrow().getUpdatedAt();
        assertThat(assignedAt).isAfter(originalCreated);
        patchBase(incident.getId(), first.getId()).andExpect(status().isOk());
        assertThat(incidents.findById(incident.getId()).orElseThrow().getUpdatedAt()).isEqualTo(assignedAt);
        patchBase(incident.getId(), second.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.knowledgeBaseId").value(second.getId().toString()));
        Instant replacedAt = incidents.findById(incident.getId()).orElseThrow().getUpdatedAt();
        assertThat(replacedAt).isAfter(assignedAt);
        patchBase(incident.getId(), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.knowledgeBaseId").value(org.hamcrest.Matchers.nullValue()));
        Instant clearedAt = incidents.findById(incident.getId()).orElseThrow().getUpdatedAt();
        assertThat(clearedAt).isAfter(replacedAt);
        patchBase(incident.getId(), null).andExpect(status().isOk());
        Incident loaded = incidents.findById(incident.getId()).orElseThrow();
        assertThat(loaded.getUpdatedAt()).isEqualTo(clearedAt);
        assertThat(loaded.getCreatedAt()).isEqualTo(originalCreated);
        assertThat(loaded.getTitle()).isEqualTo("Title");
        assertThat(loaded.getDescription()).isEqualTo("Description");
        assertThat(loaded.getSource()).isEqualTo(IncidentSource.MANUAL);
        assertThat(loaded.getStatus()).isEqualTo(dev.alexey.devassist.incident.enums.IncidentStatus.NEW);
    }

    private org.springframework.test.web.servlet.ResultActions patchBase(UUID incidentId, UUID baseId) throws Exception {
        String value = baseId == null ? "null" : "\"" + baseId + "\"";
        return mvc.perform(patch("/api/v1/incidents/{id}/knowledge-base", incidentId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"knowledgeBaseId\":" + value + "}"));
    }

    @Test void patchUnknownIdsReturn404() throws Exception {
        patchBase(UUID.randomUUID(), null).andExpect(status().isNotFound());
        Incident incident = incident();
        patchBase(incident.getId(), UUID.randomUUID()).andExpect(status().isNotFound());
        assertThat(incidents.findById(incident.getId()).orElseThrow().getKnowledgeBase()).isNull();
    }

    @Test void deletingBaseNullsIncidentForeignKeyAndDeletesKnowledgeContent() {
        KnowledgeBase base = base("deleted-service");
        KnowledgeDocument document = documents.saveAndFlush(new KnowledgeDocument(base, "runbook", "Runbook",
                KnowledgeSourceType.RUNBOOK, null, "a".repeat(64)));
        chunks.saveAndFlush(new KnowledgeChunk(document, 0, "Content", "b".repeat(64), Map.of()));
        Incident incident = incident();
        incident.assignKnowledgeBase(base);
        incidents.saveAndFlush(incident);
        UUID incidentId = incident.getId();
        Instant createdAt = incidents.findById(incidentId).orElseThrow().getCreatedAt();
        bases.deleteById(base.getId());
        bases.flush();
        entityManager.clear();
        Incident reloaded = incidents.findById(incidentId).orElseThrow();
        assertThat(reloaded.getKnowledgeBase()).isNull();
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
        assertThat(documents.count()).isZero();
        assertThat(chunks.count()).isZero();
    }

    @Test void migrationForeignKeyAndIndexArePresent() {
        String deleteRule = jdbc.queryForObject("""
                select confdeltype::text from pg_constraint where conname = 'fk_incidents_knowledge_base'
                """, String.class);
        assertThat(deleteRule).isEqualTo("n");
        assertThat(jdbc.queryForObject("select count(*) from pg_indexes where indexname = 'idx_incidents_knowledge_base'",
                Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select is_nullable from information_schema.columns
                where table_name = 'incidents' and column_name = 'knowledge_base_id'
                """, String.class)).isEqualTo("YES");
        Incident incident = incident();
        assertThat(incidents.findById(incident.getId()).orElseThrow().getKnowledgeBase()).isNull();
        UUID unknownBaseId = UUID.randomUUID();
        UUID incidentId = incident.getId();
        assertThatThrownBy(() -> jdbc.update("update incidents set knowledge_base_id = ? where id = ?",
                unknownBaseId, incidentId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void deletingIncidentDoesNotDeleteKnowledgeBase() {
        KnowledgeBase base = base("retained-service");
        Incident incident = incident();
        incident.assignKnowledgeBase(base);
        incidents.saveAndFlush(incident);
        incidents.deleteById(incident.getId());
        assertThat(bases.findById(base.getId())).isPresent();
    }

    @Test
    @Transactional
    void mapperReadsLazyKnowledgeBaseIdWithoutLoadingBase() {
        KnowledgeBase base = base("lazy-service");
        Incident incident = incident();
        incident.assignKnowledgeBase(base);
        incidents.saveAndFlush(incident);
        entityManager.clear();
        Incident loaded = incidents.findById(incident.getId()).orElseThrow();
        assertThat(Hibernate.isInitialized(loaded.getKnowledgeBase())).isFalse();
        assertThat(mapper.toResponse(loaded).knowledgeBaseId()).isEqualTo(base.getId());
        assertThat(Hibernate.isInitialized(loaded.getKnowledgeBase())).isFalse();
    }
}
