package dev.alexey.devassist.analysis.infrastructure;

import java.time.Clock;
import dev.alexey.devassist.analysis.validation.IncidentAnalysisValidator;

import dev.alexey.devassist.analysis.IncidentAnalysisGateway;
import dev.alexey.devassist.analysis.IncidentAnalysisRepairPolicy;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

	@Bean
	@DependsOn("incidentAnalysisPrompt")
	IncidentAnalysisRepairPolicy incidentAnalysisRepairPolicy(IncidentAnalysisGateway gateway) {
		return new IncidentAnalysisRepairPolicy(gateway, System::nanoTime);
	}

	@Bean
	IncidentAnalysisSchema incidentAnalysisSchema() {
		return new IncidentAnalysisSchema();
	}

	@Bean
	IncidentAnalysisConverter incidentAnalysisConverter() {
		return new IncidentAnalysisConverter(new IncidentAnalysisValidator());
	}

	@Bean
	IncidentAnalysisPrompt incidentAnalysisPrompt(AiProperties properties) {
		return new IncidentAnalysisPrompt(properties.promptVersion());
	}

	@Bean
	@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai", matchIfMissing = true)
	IncidentAnalysisGateway incidentAnalysisGateway(OpenAiChatModel chatModel, AiProperties properties,
			IncidentAnalysisPrompt prompt, Environment environment,
			IncidentAnalysisConverter converter, IncidentAnalysisSchema schema) {
		Assert.hasText(environment.getProperty("spring.ai.openai.api-key"), "OPENAI_API_KEY is required when app.ai.provider=openai");
		return new OpenAiIncidentAnalysisGateway(chatModel, properties, prompt, Clock.systemUTC(), System::nanoTime, converter, schema);
	}
}
