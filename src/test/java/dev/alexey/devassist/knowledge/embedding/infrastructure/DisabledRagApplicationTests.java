package dev.alexey.devassist.knowledge.embedding.infrastructure;

import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingGateway;
import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import dev.alexey.devassist.knowledge.entity.KnowledgeBase;
import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import dev.alexey.devassist.knowledge.ingestion.KnowledgeDocumentIngestionService;
import dev.alexey.devassist.knowledge.model.IngestKnowledgeDocumentCommand;
import dev.alexey.devassist.knowledge.model.KnowledgeDocumentIngestionStatus;
import dev.alexey.devassist.knowledge.repository.KnowledgeBaseRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeChunkRepository;
import dev.alexey.devassist.knowledge.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {"app.ai.provider=ollama", "app.rag.enabled=false",
        "app.rag.embedding.provider=unknown", "app.rag.embedding.dimensions=512",
        "app.rag.embedding.timeout=0s", "spring.ai.openai.api-key="})
@Transactional
class DisabledRagApplicationTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg17-bookworm")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired ApplicationContext context;
    @Autowired KnowledgeBaseRepository bases;
    @Autowired KnowledgeDocumentRepository documents;
    @Autowired KnowledgeChunkRepository chunks;
    @Autowired KnowledgeDocumentIngestionService ingestion;

    @Test void ollamaOnlyApplicationStartsWithoutOpenAiKeyOrEmbeddingRuntime() {
        assertThat(context.getBeansOfType(KnowledgeEmbeddingGateway.class)).isEmpty();
        assertThat(context.getBeansOfType(EmbeddingModel.class)).isEmpty();
        assertThat(context.getBeansOfType(EmbeddingProfile.class)).isEmpty();
        assertThat(context.getBeansOfType(EmbeddingProperties.class)).isEmpty();
        assertThat(bases).isNotNull();
        assertThat(documents).isNotNull();
        assertThat(chunks).isNotNull();
        assertThat(ingestion).isNotNull();
    }

    @Test void ingestionPersistsWithoutEmbeddingsWhenRagIsDisabled() {
        KnowledgeBase base = bases.saveAndFlush(new KnowledgeBase("disabled-rag", "Disabled RAG", null));
        var result = ingestion.ingest(new IngestKnowledgeDocumentCommand(base.getId(), "runbook", "Runbook",
                KnowledgeSourceType.RUNBOOK, null, "# Runbook\nNo embedding needed."));
        assertThat(result.status()).isEqualTo(KnowledgeDocumentIngestionStatus.CREATED);
        assertThat(chunks.findByDocument_IdOrderByChunkIndexAsc(result.documentId()))
                .singleElement().satisfies(chunk -> assertThat(chunk.getEmbedding()).isNull());
    }
}
