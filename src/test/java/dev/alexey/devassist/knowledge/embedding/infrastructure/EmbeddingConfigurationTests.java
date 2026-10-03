package dev.alexey.devassist.knowledge.embedding.infrastructure;

import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingGateway;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import static org.assertj.core.api.Assertions.*;

class EmbeddingConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EmbeddingConfiguration.class)
            .withPropertyValues("app.rag.embedding.openai-model=text-embedding-3-small",
                    "app.rag.embedding.ollama-model=nomic-embed-text",
                    "app.rag.embedding.dimensions=768", "app.rag.embedding.timeout=20s",
                    "app.rag.embedding.batch-size=32", "app.rag.embedding.max-chunks-per-run=500",
                    "app.ai.ollama-base-url=http://127.0.0.1:1", "app.rag.enabled=true");

    @Test void disabledFeatureCreatesNoEmbeddingBeansAndIgnoresInvalidProperties() {
        runner.withPropertyValues("app.rag.enabled=false", "app.rag.embedding.provider=unknown",
                "app.rag.embedding.dimensions=512", "app.rag.embedding.timeout=0s",
                "app.rag.embedding.batch-size=0", "app.rag.embedding.max-chunks-per-run=0")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(KnowledgeEmbeddingGateway.class);
                    assertThat(context).doesNotHaveBean(KnowledgeEmbeddingOrchestrator.class);
                    assertThat(context).doesNotHaveBean(EmbeddingProfile.class);
                    assertThat(context).doesNotHaveBean(EmbeddingProperties.class);
                    assertThat(context).doesNotHaveBean(OpenAiEmbeddingOptions.class);
                    assertThat(context).doesNotHaveBean(OllamaEmbeddingOptions.class);
                });
    }

    @Test void openAiSelectionCreatesOnlyOpenAiGatewayAndRequests768Dimensions() {
        runner.withPropertyValues("app.rag.embedding.provider=openai", "spring.ai.openai.api-key=fake-key",
                "app.ai.provider=ollama").run(context -> {
            assertThat(context).hasSingleBean(KnowledgeEmbeddingGateway.class);
            assertThat(context.getBean(KnowledgeEmbeddingGateway.class))
                    .isInstanceOf(OpenAiKnowledgeEmbeddingGateway.class);
            OpenAiEmbeddingOptions options = context.getBean(OpenAiEmbeddingOptions.class);
            assertThat(options.getDimensions()).isEqualTo(768);
            assertThat(options.toOpenAiCreateParams(java.util.List.of("text")).dimensions().orElseThrow()).isEqualTo(768L);
            assertThat(context.getBean(EmbeddingProfile.class))
                    .isEqualTo(new EmbeddingProfile("openai", "text-embedding-3-small", 768));
        });
    }

    @Test void ollamaSelectionNeedsNoOpenAiKeyForEmbeddingGateway() {
        runner.withPropertyValues("app.rag.embedding.provider=ollama", "app.ai.provider=openai")
                .run(context -> {
                    assertThat(context).hasSingleBean(KnowledgeEmbeddingGateway.class);
                    assertThat(context.getBean(KnowledgeEmbeddingGateway.class))
                            .isInstanceOf(OllamaKnowledgeEmbeddingGateway.class);
                    assertThat(context.getBean(OllamaEmbeddingOptions.class).getModel())
                            .isEqualTo("nomic-embed-text");
                });
    }

    @Test void unknownProviderAndDimensionsFailFast() {
        runner.withPropertyValues("app.rag.embedding.provider=unknown")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.rag.embedding.provider=ollama", "app.rag.embedding.dimensions=512")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void invalidTimeoutFailsFast() {
        runner.withPropertyValues("app.rag.embedding.provider=ollama", "app.rag.embedding.timeout=0s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void invalidBatchAndRunLimitsFailFast() {
        for (String property : java.util.List.of("app.rag.embedding.batch-size", "app.rag.embedding.max-chunks-per-run")) {
            runner.withPropertyValues("app.rag.embedding.provider=ollama", property + "=0")
                    .run(context -> assertThat(context).hasFailed());
        }
        runner.withPropertyValues("app.rag.embedding.provider=ollama", "app.rag.embedding.batch-size=257")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.rag.embedding.provider=ollama", "app.rag.embedding.max-chunks-per-run=10001")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void openAiRequiresKeyOnlyWhenSelected() {
        runner.withPropertyValues("app.rag.embedding.provider=openai")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void propertyOverridesSelectModelAndTimeout() {
        runner.withPropertyValues("app.rag.embedding.provider=ollama",
                "app.rag.embedding.ollama-model=nomic-embed-text:v1.5",
                "app.rag.embedding.timeout=7s", "app.rag.embedding.batch-size=8",
                "app.rag.embedding.max-chunks-per-run=42")
                .run(context -> {
                    assertThat(context.getBean(OllamaEmbeddingOptions.class).getModel())
                            .isEqualTo("nomic-embed-text:v1.5");
                    assertThat(context.getBean(EmbeddingProperties.class).timeout())
                            .isEqualTo(java.time.Duration.ofSeconds(7));
                    assertThat(context.getBean(EmbeddingProperties.class).batchSize()).isEqualTo(8);
                    assertThat(context.getBean(EmbeddingProperties.class).maxChunksPerRun()).isEqualTo(42);
                });
    }

    @Test void applicationYamlDefaultsAndEnvironmentOverridesBind() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(EmbeddingConfiguration.class)
                .withPropertyValues("RAG_EMBEDDING_PROVIDER=ollama",
                        "RAG_ENABLED=true",
                        "OLLAMA_EMBEDDING_MODEL=nomic-embed-text:v1.5",
                        "RAG_EMBEDDING_TIMEOUT=7s", "OLLAMA_BASE_URL=http://127.0.0.1:1")
                .run(context -> {
                    assertThat(context).hasSingleBean(KnowledgeEmbeddingGateway.class);
                    EmbeddingProperties properties = context.getBean(EmbeddingProperties.class);
                    assertThat(properties.provider()).isEqualTo("ollama");
                    assertThat(properties.ollamaModel()).isEqualTo("nomic-embed-text:v1.5");
                    assertThat(properties.openaiModel()).isEqualTo("text-embedding-3-small");
                    assertThat(properties.dimensions()).isEqualTo(768);
                    assertThat(properties.timeout()).isEqualTo(java.time.Duration.ofSeconds(7));
                    assertThat(properties.batchSize()).isEqualTo(32);
                    assertThat(properties.maxChunksPerRun()).isEqualTo(500);
                });
    }
}
