package dev.alexey.devassist.knowledge.embedding.infrastructure;

import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import dev.alexey.devassist.knowledge.embedding.KnowledgeEmbeddingGateway;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.rag.enabled", havingValue = "true")
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingConfiguration {
    @Bean
    EmbeddingProfile activeEmbeddingProfile(EmbeddingProperties properties) {
        String model = "openai".equals(properties.provider())
                ? properties.openaiModel() : properties.ollamaModel();
        return new EmbeddingProfile(properties.provider(), model, properties.dimensions());
    }

    @Bean
    @ConditionalOnProperty(name = "app.rag.embedding.provider", havingValue = "openai", matchIfMissing = true)
    OpenAiEmbeddingOptions openAiEmbeddingOptions(EmbeddingProperties properties, Environment environment) {
        String apiKey = environment.getProperty("spring.ai.openai.api-key");
        Assert.hasText(apiKey, "OPENAI_API_KEY is required when RAG_EMBEDDING_PROVIDER=openai");
        return OpenAiEmbeddingOptions.builder()
                .apiKey(apiKey).model(properties.openaiModel()).dimensions(properties.dimensions())
                .timeout(properties.timeout()).maxRetries(0).build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.rag.embedding.provider", havingValue = "openai", matchIfMissing = true)
    KnowledgeEmbeddingGateway openAiKnowledgeEmbeddingGateway(OpenAiEmbeddingOptions options,
            EmbeddingProfile profile) {
        OpenAiEmbeddingModel model = OpenAiEmbeddingModel.builder().options(options).build();
        return new OpenAiKnowledgeEmbeddingGateway(model, profile, System::nanoTime);
    }

    @Bean
    @ConditionalOnProperty(name = "app.rag.embedding.provider", havingValue = "ollama")
    OllamaEmbeddingOptions ollamaEmbeddingOptions(EmbeddingProperties properties) {
        return OllamaEmbeddingOptions.builder().model(properties.ollamaModel()).build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.rag.embedding.provider", havingValue = "ollama")
    KnowledgeEmbeddingGateway ollamaKnowledgeEmbeddingGateway(EmbeddingProperties properties,
            EmbeddingProfile profile, Environment environment, OllamaEmbeddingOptions options) {
        String baseUrl = environment.getRequiredProperty("app.ai.ollama-base-url");
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        OllamaApi api = OllamaApi.builder().baseUrl(baseUrl)
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory)).build();
        OllamaEmbeddingModel model = OllamaEmbeddingModel.builder().ollamaApi(api).options(options).build();
        return new OllamaKnowledgeEmbeddingGateway(model, profile, System::nanoTime);
    }
}
