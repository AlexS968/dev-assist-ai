package dev.alexey.devassist.analysis.infrastructure;

import dev.alexey.devassist.analysis.IncidentAnalysisGateway;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withInitializer(context -> {
				// Do not read a developer's credentials or model selection in configuration tests.
				context.getEnvironment().getPropertySources().remove("systemEnvironment");
				context.getEnvironment().getPropertySources().remove("systemProperties");
				new ConfigDataApplicationContextInitializer().initialize(context);
			})
			.withConfiguration(AutoConfigurations.of(
					OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.withUserConfiguration(AiConfiguration.class)
			.withPropertyValues("spring.ai.openai.api-key=${OPENAI_API_KEY}",
					"OPENAI_API_KEY=not-a-real-context-test-key",
					"spring.ai.openai.base-url=http://127.0.0.1:1");

	@Test
	void createsGatewayAndOpenAiModelWithoutNetworkCalls() {
		contextRunner.run(context -> {
			assertThat(context).hasNotFailed().hasSingleBean(IncidentAnalysisGateway.class);
			assertThat(context.getBean(IncidentAnalysisGateway.class))
					.isInstanceOf(OpenAiIncidentAnalysisGateway.class);
			assertThat(context.getBean(AiProperties.class).model()).isEqualTo("gpt-6-luna");
			assertThat(context.getBean(AiProperties.class).promptVersion()).isEqualTo("incident-analysis-v1");
			assertThat(context.getBean(AiProperties.class).maxOutputTokens()).isEqualTo(450);
			assertThat(context.getBean(IncidentAnalysisPrompt.class).version()).isEqualTo("incident-analysis-v1");
			assertThat(context.getBean(OpenAiChatModel.class).getOptions().getModel())
					.isEqualTo("gpt-6-luna");
			var properties = context.getBean(OpenAiCommonProperties.class);
			assertThat(properties.getApiKey()).isEqualTo("not-a-real-context-test-key");
			assertThat(properties.getMaxRetries()).isZero();
			assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(20));
			assertThat(context.getBean(AiProperties.class).timeout()).isEqualTo(Duration.ofSeconds(20));
		});
	}

	@Test
	void modelCanBeOverriddenThroughEnvironmentPlaceholder() {
		contextRunner.withPropertyValues("OPENAI_MODEL=test-model").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(AiProperties.class).model()).isEqualTo("test-model");
			assertThat(context.getBean(OpenAiChatModel.class).getOptions().getModel())
					.isEqualTo("test-model");
		});
	}

	@Test
	void rejectsBlankModelConfiguration() {
		contextRunner.withPropertyValues("app.ai.model=").run(context ->
				assertThat(context).hasFailed());
	}

	@Test
	void overridesPromptSettingsThroughEnvironmentPlaceholders() {
		contextRunner.withPropertyValues("AI_PROMPT_VERSION=incident-analysis-v1", "OPENAI_MAX_OUTPUT_TOKENS=300")
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context.getBean(AiProperties.class).promptVersion()).isEqualTo("incident-analysis-v1");
					assertThat(context.getBean(AiProperties.class).maxOutputTokens()).isEqualTo(300);
				});
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " "})
	void rejectsBlankPromptVersion(String version) {
		contextRunner.withPropertyValues("AI_PROMPT_VERSION=" + version).run(context ->
				assertThat(context).hasFailed().getFailure().hasRootCauseInstanceOf(BindValidationException.class));
	}

	@ParameterizedTest
	@ValueSource(ints = {-1, 0, 16385})
	void rejectsInvalidOutputTokenLimit(int limit) {
		contextRunner.withPropertyValues("OPENAI_MAX_OUTPUT_TOKENS=" + limit).run(context ->
				assertThat(context).hasFailed().getFailure().hasRootCauseInstanceOf(BindValidationException.class));
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 16384})
	void acceptsOutputTokenLimitBoundaries(int limit) {
		contextRunner.withPropertyValues("OPENAI_MAX_OUTPUT_TOKENS=" + limit).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(AiProperties.class).maxOutputTokens()).isEqualTo(limit);
		});
	}

	@Test
	void rejectsUnknownPromptVersionAtStartup() {
		contextRunner.withPropertyValues("AI_PROMPT_VERSION=incident-analysis-v999").run(context ->
				assertThat(context).hasFailed().getFailure().hasRootCauseInstanceOf(IllegalArgumentException.class));
	}

	@ParameterizedTest
	@CsvSource({"20s,20000", "750ms,750", "PT3S,3000"})
	void appliesTimeoutToActualTransportBuilderWithoutNetwork(String timeout, int expectedMillis) {
		List<Integer> callTimeouts = new ArrayList<>();
		contextRunner.withPropertyValues("AI_TIMEOUT=" + timeout)
				.withBean(OpenAiHttpClientBuilderCustomizer.class, () -> builder -> {
					// OpenAiSetup invokes this after applying the configured timeout.
					// Build and inspect the actual OkHttp client; never execute a call.
					try (var transport = builder.build()) {
						callTimeouts.add(transport.getOkHttpClient().callTimeoutMillis());
					}
				})
				.run(context -> {
					assertThat(context).hasNotFailed();
					var expected = Duration.ofMillis(expectedMillis);
					assertThat(context.getBean(AiProperties.class).timeout()).isEqualTo(expected);
					assertThat(context.getBean(OpenAiCommonProperties.class).getTimeout()).isEqualTo(expected);
					assertThat(context.getBean(OpenAiCommonProperties.class).getMaxRetries()).isZero();
					assertThat(callTimeouts).containsExactly((int) expected.toMillis(), (int) expected.toMillis());
				});
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "0s", "-1s", "PT-0.5S", "1ns", "2147483648ms"})
	void rejectsInvalidTimeoutAtStartup(String timeout) {
		contextRunner.withPropertyValues("AI_TIMEOUT=" + timeout).run(context ->
				assertThat(context).hasFailed());
	}
}
