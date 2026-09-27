package dev.alexey.devassist.analysis.infrastructure;

import java.time.Clock;

import dev.alexey.devassist.analysis.IncidentAnalysisGateway;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {

	@Bean
	IncidentAnalysisPrompt incidentAnalysisPrompt(AiProperties properties) {
		return new IncidentAnalysisPrompt(properties.promptVersion());
	}

	@Bean
	IncidentAnalysisGateway incidentAnalysisGateway(OpenAiChatModel chatModel, AiProperties properties,
			IncidentAnalysisPrompt prompt) {
		return new OpenAiIncidentAnalysisGateway(chatModel, properties, prompt, Clock.systemUTC(), System::nanoTime);
	}
}
