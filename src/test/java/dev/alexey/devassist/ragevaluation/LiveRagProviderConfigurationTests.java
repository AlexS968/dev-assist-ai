package dev.alexey.devassist.ragevaluation;

import java.util.Map;
import java.util.HashMap;
import java.time.Duration;
import dev.alexey.devassist.knowledge.embedding.infrastructure.EmbeddingProperties;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.*;

class LiveRagProviderConfigurationTests {
    @Test void ollamaRequiresOnlySelectorAndUsesLiveDefaults() {
        var properties = LiveRagProviderConfiguration.resolve(Map.of("RAG_LIVE_PROVIDER", "ollama"));
        assertThat(properties).containsEntry("app.ai.provider", "ollama")
                .containsEntry("app.rag.embedding.provider", "ollama")
                .containsEntry("app.rag.enabled", "true")
                .containsEntry("app.ai.prompt-version", "incident-analysis-v3")
                .containsEntry("app.rag.embedding.dimensions", "768")
                .containsEntry("app.ai.max-output-tokens", "1000")
                .containsEntry("app.ai.timeout", "60s")
                .containsEntry("app.rag.embedding.timeout", "60s");
    }

    @Test void openaiRequiresKeyWithoutExposingIt() {
        Map<String, String> missingKey = Map.of("RAG_LIVE_PROVIDER", "openai");
        assertThatThrownBy(() -> LiveRagProviderConfiguration.resolve(missingKey))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("OPENAI_API_KEY");
        String secret = "test-secret-never-log";
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var properties = LiveRagProviderConfiguration.resolve(Map.of(
                    "RAG_LIVE_PROVIDER", "openai", "OPENAI_API_KEY", secret));
            assertThat(properties).containsEntry("app.ai.provider", "openai")
                    .containsEntry("app.rag.embedding.provider", "openai")
                    .containsEntry("spring.ai.openai.base-url", LiveRagProviderConfiguration.OPENAI_BASE_URL);
            assertThat(properties).containsEntry("spring.ai.openai.api-key", secret);
            assertThat(appender.list).noneMatch(event -> event.getFormattedMessage().contains(secret));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test void dynamicRegistryReceivesEnvironmentKeyAndReplacesOfflineEndpoint() {
        String secret = "test-secret-never-log";
        Map<String, Object> registered = new HashMap<>();
        LiveRagProviderConfiguration.register(Map.of("RAG_LIVE_PROVIDER", "openai",
                "OPENAI_API_KEY", secret), (name, supplier) -> registered.put(name, supplier.get()));
        assertThat(registered).containsEntry("spring.ai.openai.api-key", secret)
                .containsEntry("spring.ai.openai.base-url", "https://api.openai.com/v1")
                .doesNotContainEntry("spring.ai.openai.base-url", "http://127.0.0.1:1");
    }

    @Test void openaiEmbeddingOptionsUseEnvironmentKeyAndOfficialEndpoint() {
        String secret = "test-secret-never-log";
        var properties = new EmbeddingProperties("openai", "text-embedding-3-small",
                "nomic-embed-text", 768, Duration.ofSeconds(60), 32, 500);
        var options = LiveOpenAiEmbeddingOptionsConfiguration.options(properties, secret);
        assertThat(options.getApiKey()).isEqualTo(secret);
        assertThat(options.getBaseUrl()).isEqualTo(LiveRagProviderConfiguration.OPENAI_BASE_URL);
        assertThat(options.getModel()).isEqualTo("text-embedding-3-small");
        assertThat(options.getMaxRetries()).isZero();
    }

    @Test void missingAndUnknownSelectorsFailSafely() {
        Map<String, String> missingSelector = Map.of();
        Map<String, String> unknownSelector = Map.of("RAG_LIVE_PROVIDER", "auto");
        assertThatThrownBy(() -> LiveRagProviderConfiguration.resolve(missingSelector))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("RAG_LIVE_PROVIDER");
        assertThatThrownBy(() -> LiveRagProviderConfiguration.resolve(unknownSelector))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Unsupported");
    }

    @Test void timeoutOverridesAreRespectedAndIncompatibleCoreSettingsFail() {
        var properties = LiveRagProviderConfiguration.resolve(Map.of(
                "RAG_LIVE_PROVIDER", "ollama", "AI_TIMEOUT", "75s",
                "RAG_EMBEDDING_TIMEOUT", "80s"));
        assertThat(properties).containsEntry("app.ai.timeout", "75s")
                .containsEntry("app.rag.embedding.timeout", "80s");
        Map<String, String> wrongDimensions = Map.of(
                "RAG_LIVE_PROVIDER", "ollama", "RAG_EMBEDDING_DIMENSIONS", "384");
        assertThatThrownBy(() -> LiveRagProviderConfiguration.resolve(wrongDimensions))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("768");
    }
}
