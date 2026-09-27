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
	IncidentAnalysisGateway incidentAnalysisGateway(OpenAiChatModel chatModel, AiProperties properties) {
		return new OpenAiIncidentAnalysisGateway(chatModel, properties.model(), Clock.systemUTC(), System::nanoTime);
	}
}
