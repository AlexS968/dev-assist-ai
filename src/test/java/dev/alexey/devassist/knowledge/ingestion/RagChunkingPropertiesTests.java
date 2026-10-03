package dev.alexey.devassist.knowledge.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;

class RagChunkingPropertiesTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test void invalidConfigurationFailsAtContextStartup() {
        runner.withPropertyValues("app.rag.chunking.max-chars=200", "app.rag.chunking.overlap-chars=200")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void validConfigurationBinds() {
        runner.withPropertyValues("app.rag.chunking.max-chars=1500", "app.rag.chunking.overlap-chars=200")
                .run(context -> assertThat(context.getBean(RagChunkingProperties.class).overlapChars()).isEqualTo(200));
    }

    @EnableConfigurationProperties(RagChunkingProperties.class)
    static class PropertiesConfiguration {}
}
