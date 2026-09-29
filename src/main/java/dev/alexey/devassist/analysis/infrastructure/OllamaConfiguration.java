package dev.alexey.devassist.analysis.infrastructure;

import dev.alexey.devassist.analysis.IncidentAnalysisGateway;
import java.time.Clock;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "ollama")
public class OllamaConfiguration {

	@Bean
	SimpleClientHttpRequestFactory ollamaRequestFactory(AiProperties properties) {
		var factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(properties.timeout());
		factory.setReadTimeout(properties.timeout());
		return factory;
	}

	@Bean
	OllamaApi ollamaApi(AiProperties properties, SimpleClientHttpRequestFactory ollamaRequestFactory) {
		return OllamaApi.builder().baseUrl(properties.ollamaBaseUrl())
				.restClientBuilder(RestClient.builder().requestFactory(ollamaRequestFactory)).build();
	}

	@Bean
	IncidentAnalysisGateway ollamaIncidentAnalysisGateway(OllamaApi api, AiProperties properties,
			IncidentAnalysisPrompt prompt, IncidentAnalysisConverter converter, IncidentAnalysisSchema schema) {
		return new OllamaIncidentAnalysisGateway(api, properties, prompt, Clock.systemUTC(), System::nanoTime, converter, schema);
	}
}
