package dev.alexey.devassist.analysis.infrastructure;

import dev.alexey.devassist.analysis.IncidentAnalysisGateway;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiProviderSelectionTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withInitializer(context -> {
				context.getEnvironment().getPropertySources().remove("systemEnvironment");
				context.getEnvironment().getPropertySources().remove("systemProperties");
				new ConfigDataApplicationContextInitializer().initialize(context);
			})
			.withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.withUserConfiguration(AiConfiguration.class, OllamaConfiguration.class)
			// Override the global test dummy key: unresolved unless a test supplies it.
			.withPropertyValues("spring.ai.openai.api-key=${OPENAI_API_KEY}");

	@Test
	void defaultOpenAiCreatesExactlyOneGateway() {
		runner.withPropertyValues("OPENAI_API_KEY=not-a-real-test-key").run(context -> {
			assertThat(context).hasNotFailed().hasSingleBean(IncidentAnalysisGateway.class).hasSingleBean(OpenAiChatModel.class);
			assertThat(context).doesNotHaveBean(OllamaApi.class);
			assertThat(context.getBean(IncidentAnalysisGateway.class)).isInstanceOf(OpenAiIncidentAnalysisGateway.class);
			assertThat(context.getBean(AiProperties.class).provider()).isEqualTo("openai");
		});
	}

	@Test
	void ollamaStartsWithoutAnyOpenAiKeyAndCreatesOnlyOllamaGateway() {
		runner.withPropertyValues("AI_PROVIDER=ollama").run(context -> {
			assertThat(context).hasNotFailed().hasSingleBean(IncidentAnalysisGateway.class).hasSingleBean(OllamaApi.class);
			assertThat(context).doesNotHaveBean(OpenAiChatModel.class);
			assertThat(context.getEnvironment().getProperty("OPENAI_API_KEY")).isNull();
			assertThat(context.getBean(IncidentAnalysisGateway.class)).isInstanceOf(OllamaIncidentAnalysisGateway.class);
			assertThat(context.getBean(AiProperties.class).ollamaModel()).isEqualTo("qwen3:14b");
			assertThat(context.getBean(AiProperties.class).ollamaBaseUrl()).isEqualTo("http://localhost:11434");
		});
	}

	@Test
	void openAiStillRequiresKey() {
		runner.withPropertyValues("AI_PROVIDER=openai").run(context -> assertThat(context).hasFailed());
	}

	@ParameterizedTest
	@ValueSource(strings = {"unknown", "OPENAI", ""})
	void rejectsUnsupportedProvider(String provider) {
		runner.withPropertyValues("AI_PROVIDER=" + provider).run(context ->
				assertThat(context).hasFailed().getFailure().hasStackTraceContaining("AI provider must be openai or ollama"));
	}

	@Test
	void appliesOllamaOverridesAndConfiguresConnectAndReadTimeouts() {
		try (var factories = mockConstruction(SimpleClientHttpRequestFactory.class)) {
			runner.withPropertyValues("AI_PROVIDER=ollama", "OLLAMA_MODEL=custom-model",
					"OLLAMA_BASE_URL=http://127.0.0.1:11435", "AI_TIMEOUT=3s").run(context -> {
				assertThat(context).hasNotFailed();
				var properties = context.getBean(AiProperties.class);
				assertThat(properties.ollamaModel()).isEqualTo("custom-model");
				assertThat(properties.ollamaBaseUrl()).isEqualTo("http://127.0.0.1:11435");
				var factory = context.getBean(SimpleClientHttpRequestFactory.class);
				verify(factory).setConnectTimeout(Duration.ofSeconds(3));
				verify(factory).setReadTimeout(Duration.ofSeconds(3));
			});
		}
	}
}
