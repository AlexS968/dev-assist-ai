package dev.alexey.devassist.analysis.grounding;

import dev.alexey.devassist.knowledge.retrieval.KnowledgeRetrievalService;
import dev.alexey.devassist.knowledge.retrieval.RetrievalConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GroundingConfigurationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(GroundingConfiguration.class, RetrievalConfiguration.class)
            .withBean(KnowledgeRetrievalService.class, () -> mock(KnowledgeRetrievalService.class));

    @Test
    void defaultsAndEnvironmentOverridesBind() {
        runner.withPropertyValues("app.rag.enabled=true", "RAG_ENABLED=true")
                .run(context -> assertThat(context.getBean(GroundingProperties.class))
                        .isEqualTo(new GroundingProperties(6000, 5)));
        runner.withPropertyValues("app.rag.enabled=true", "RAG_ENABLED=true", "RAG_MAX_CONTEXT_CHARS=7000",
                "RAG_MAX_CONTEXT_CHUNKS=3")
                .run(context -> assertThat(context.getBean(GroundingProperties.class))
                        .isEqualTo(new GroundingProperties(7000, 3)));
    }

    @Test
    void invalidLimitsFailOnlyWhenRagIsEnabled() {
        runner.withPropertyValues("app.rag.enabled=true", "RAG_ENABLED=true", "RAG_MAX_CONTEXT_CHARS=499")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.rag.enabled=true", "RAG_ENABLED=true", "RAG_MAX_CONTEXT_CHUNKS=21")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.rag.enabled=true", "RAG_ENABLED=true", "RAG_MAX_CONTEXT_CHUNKS=6",
                "RAG_RETRIEVAL_MAX_TOP_K=5")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("RAG_ENABLED=false", "RAG_MAX_CONTEXT_CHARS=1",
                "RAG_MAX_CONTEXT_CHUNKS=99")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(GroundingProperties.class);
                });
    }
}
