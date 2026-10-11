package dev.alexey.devassist.knowledge.embedding.infrastructure;

import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {"app.ai.provider=ollama", "app.rag.enabled=true",
        "app.rag.embedding.provider=ollama", "spring.ai.openai.api-key="})
class EnabledOllamaRagApplicationTests {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg17-bookworm")
                    .asCompatibleSubstituteFor("postgres"));
    @Autowired ApplicationContext context;

    @Test void ollamaChatAndEmbeddingsStartWithoutOpenAiKey() {
        assertThat(context.getBeansOfType(KnowledgeEmbeddingGateway.class)).hasSize(1);
        assertThat(context.getBean(KnowledgeEmbeddingGateway.class))
                .isInstanceOf(OllamaKnowledgeEmbeddingGateway.class);
    }
}
