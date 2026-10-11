package dev.alexey.devassist.knowledge.retrieval;

import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class RetrievalConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RetrievalConfiguration.class)
            .withPropertyValues("app.rag.enabled=true", "app.rag.retrieval.top-k=5",
                    "app.rag.retrieval.max-top-k=20",
                    "app.rag.retrieval.openai-similarity-threshold=0.60",
                    "app.rag.retrieval.ollama-similarity-threshold=0.70");

    @Test void defaultsAndOverridesBind() {
        new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(RetrievalConfiguration.class)
                .withPropertyValues("RAG_ENABLED=true", "RAG_RETRIEVAL_TOP_K=4",
                        "RAG_RETRIEVAL_MAX_TOP_K=9",
                        "RAG_RETRIEVAL_OPENAI_SIMILARITY_THRESHOLD=0.55",
                        "RAG_RETRIEVAL_OLLAMA_SIMILARITY_THRESHOLD=0.8")
                .run(context -> assertThat(context.getBean(RetrievalProperties.class))
                        .isEqualTo(new RetrievalProperties(4, 9, 0.55, 0.8)));
        runner.run(context -> assertThat(context.getBean(RetrievalProperties.class))
                .isEqualTo(new RetrievalProperties(5, 20, 0.60, 0.70)));
    }

    @Test void activeEmbeddingProviderSelectsDefaultThreshold() {
        runner.run(context -> {
            RetrievalProperties properties = context.getBean(RetrievalProperties.class);
            assertThat(properties.thresholdFor(new EmbeddingProfile("openai", "embedding-model", 768)))
                    .isEqualTo(0.60);
            assertThat(properties.thresholdFor(new EmbeddingProfile("ollama", "embedding-model", 768)))
                    .isEqualTo(0.70);
        });
    }

    @Test void invalidRangesFailOnlyWhenEnabled() {
        runner.withPropertyValues("app.rag.retrieval.top-k=21")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.rag.retrieval.max-top-k=101")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.rag.retrieval.openai-similarity-threshold=-0.1")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.rag.retrieval.ollama-similarity-threshold=1.1")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.rag.enabled=false", "app.rag.retrieval.top-k=999",
                "app.rag.retrieval.openai-similarity-threshold=-1",
                "app.rag.retrieval.ollama-similarity-threshold=2")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RetrievalProperties.class);
                });
    }
}
