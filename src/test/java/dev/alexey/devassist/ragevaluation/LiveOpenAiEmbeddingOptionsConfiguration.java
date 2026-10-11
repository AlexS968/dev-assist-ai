package dev.alexey.devassist.ragevaluation;

import dev.alexey.devassist.knowledge.embedding.infrastructure.EmbeddingProperties;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.env.Environment;

/** Live-only override: EmbeddingConfiguration builds its SDK client from options, not Spring AI chat properties. */
@TestConfiguration(proxyBeanMethods = false)
class LiveOpenAiEmbeddingOptionsConfiguration {
    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.rag.embedding.provider", havingValue = "openai")
    OpenAiEmbeddingOptions liveOpenAiEmbeddingOptions(EmbeddingProperties properties, Environment environment) {
        return options(properties, environment.getRequiredProperty("spring.ai.openai.api-key"));
    }

    static OpenAiEmbeddingOptions options(EmbeddingProperties properties, String apiKey) {
        return OpenAiEmbeddingOptions.builder()
                .apiKey(apiKey)
                .baseUrl(LiveRagProviderConfiguration.OPENAI_BASE_URL)
                .model(properties.openaiModel())
                .dimensions(properties.dimensions())
                .timeout(properties.timeout())
                .maxRetries(0)
                .build();
    }
}
