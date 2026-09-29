package dev.alexey.devassist.analysis.infrastructure;

import dev.alexey.devassist.analysis.IncidentAnalysisInput;
import dev.alexey.devassist.analysis.exception.IncidentAnalysisConversionException;
import dev.alexey.devassist.analysis.exception.IncidentAnalysisValidationException;
import org.junit.jupiter.params.provider.MethodSource;
import dev.alexey.devassist.analysis.validation.IncidentAnalysisValidator;
import static dev.alexey.devassist.analysis.StructuredAnalysisFixtures.*;
import dev.alexey.devassist.analysis.exception.EmptyIncidentAnalysisException;
import dev.alexey.devassist.analysis.exception.IncidentAnalysisException;
import java.time.Clock;
import java.time.Duration;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import com.openai.errors.OpenAIIoException;
import dev.alexey.devassist.analysis.exception.IncidentAnalysisTimeoutException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpenAiIncidentAnalysisGatewayTests {

	private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");
	private final ChatModel model = mock(ChatModel.class);
	private final IncidentAnalysisInput input = new IncidentAnalysisInput("Database unavailable", "Request failed: {\"code\":503}");

	private OpenAiIncidentAnalysisGateway gateway() {
		var ticks = new AtomicLong(100_000_000L);
		return new OpenAiIncidentAnalysisGateway(model, new AiProperties("configured-model", "incident-analysis-v2", 321, Duration.ofSeconds(20), "openai", "qwen3:14b", "http://localhost:11434"),
				new IncidentAnalysisPrompt("incident-analysis-v2"),
				Clock.fixed(NOW, ZoneOffset.UTC), () -> ticks.getAndAdd(125_000_000L),
				new IncidentAnalysisConverter(new IncidentAnalysisValidator()), new IncidentAnalysisSchema());
	}

	@Test
	void constructionDoesNotCallModel() {
		gateway();
		verify(model, never()).call(any(Prompt.class));
	}

	@Test
	void mapsContentMetadataAndMeasuredTime() {
		when(model.call(any(Prompt.class))).thenReturn(response(JSON,
				ChatResponseMetadata.builder().model("reported-model").usage(new DefaultUsage(120, 80, 200)).build()));

		var result = gateway().analyze(input);

		assertThat(result.analysis()).isEqualTo(analysis());
		assertThat(result.provider()).isEqualTo("openai");
		assertThat(result.promptVersion()).isEqualTo("incident-analysis-v2");
		assertThat(result.model()).isEqualTo("reported-model");
		assertThat(result.generatedAt()).isEqualTo(NOW);
		assertThat(result.latencyMs()).isEqualTo(125);
		assertThat(result.inputTokens()).isEqualTo(120);
		assertThat(result.outputTokens()).isEqualTo(80);
		assertThat(result.totalTokens()).isEqualTo(200);
		var prompt = ArgumentCaptor.forClass(Prompt.class);
		verify(model).call(prompt.capture());
		assertThat(prompt.getValue().getInstructions()).hasSize(2);
		assertThat(prompt.getValue().getOptions()).isInstanceOf(OpenAiChatOptions.class);
		var options = (OpenAiChatOptions) prompt.getValue().getOptions();
		assertThat(options.getMaxCompletionTokens()).isEqualTo(321);
		assertThat(options.getResponseFormat().getType()).isEqualTo(org.springframework.ai.openai.OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA);
		assertThat(options.getResponseFormat().getStrict()).isTrue();
		assertThat(options.getResponseFormat().getJsonSchema()).isEqualTo(new IncidentAnalysisSchema().json());
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(prompt.getValue().getInstructions().getFirst().getText())
				.isEqualTo(new IncidentAnalysisPrompt("incident-analysis-v2").system())
				.doesNotContain(input.title(), input.description());
		assertThat(prompt.getValue().getInstructions().getFirst().getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(prompt.getValue().getInstructions().getLast().getMessageType()).isEqualTo(MessageType.USER);
		assertThat(prompt.getValue().getInstructions().getLast().getText())
				.isEqualTo("Title: Database unavailable\nDescription: Request failed: {\"code\":503}");
	}

	@Test
	void missingUsageStaysNullAndMissingModelUsesConfiguredModel() {
		when(model.call(any(Prompt.class))).thenReturn(response(JSON, ChatResponseMetadata.builder().build()));
		var result = gateway().analyze(input);
		assertThat(result.model()).isEqualTo("configured-model");
		assertThat(result.inputTokens()).isNull();
		assertThat(result.outputTokens()).isNull();
		assertThat(result.totalTokens()).isNull();
	}

	@Test
	void preservesActuallyReportedZeroTokens() {
		when(model.call(any(Prompt.class))).thenReturn(response(JSON,
				ChatResponseMetadata.builder().usage(new DefaultUsage(0, 0, 0)).build()));
		var result = gateway().analyze(input);
		assertThat(result.inputTokens()).isZero();
		assertThat(result.outputTokens()).isZero();
		assertThat(result.totalTokens()).isZero();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\n\t"})
	void rejectsEmptyContent(String content) {
		when(model.call(any(Prompt.class))).thenReturn(response(content, ChatResponseMetadata.builder().build()));
		var gateway = gateway();
		assertThatThrownBy(() -> gateway.analyze(input)).isInstanceOf(EmptyIncidentAnalysisException.class);
	}

	@Test
	void defaultMetadataUsesConfiguredModelAndNullTokenCounts() {
		// The metadata-free constructor supplies default metadata and EmptyUsage.
		var response = new ChatResponse(List.of(new Generation(new AssistantMessage(JSON))));
		assertThat(response.getMetadata()).isNotNull();
		when(model.call(any(Prompt.class))).thenReturn(response);

		var result = gateway().analyze(input);

		assertThat(result.analysis()).isEqualTo(analysis());
		assertThat(result.provider()).isEqualTo("openai");
		assertThat(result.promptVersion()).isEqualTo("incident-analysis-v2");
		assertThat(result.model()).isEqualTo("configured-model");
		assertThat(result.generatedAt()).isEqualTo(NOW);
		assertThat(result.latencyMs()).isEqualTo(125);
		assertThat(result.inputTokens()).isNull();
		assertThat(result.outputTokens()).isNull();
		assertThat(result.totalTokens()).isNull();
	}

	@Test
	void rejectsResponseWithoutGenerations() {
		when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));
		var gateway = gateway();
		assertThatThrownBy(() -> gateway.analyze(input)).isInstanceOf(EmptyIncidentAnalysisException.class);
	}

	@Test
	void translatesProviderFailureWithoutRetry() {
		var failure = new IllegalStateException("fake-key and private prompt");
		when(model.call(any(Prompt.class))).thenThrow(failure);
		var gateway = gateway();
		assertThatThrownBy(() -> gateway.analyze(input))
				.isInstanceOf(IncidentAnalysisException.class)
				.hasMessage("Incident analysis provider failed.").hasCause(failure);
		verify(model).call(any(Prompt.class));
	}

	@Test
	void translatesSdkSocketTimeoutWithoutRetry() {
		var failure = new OpenAIIoException("private provider URL", new SocketTimeoutException("secret details"));
		when(model.call(any(Prompt.class))).thenThrow(failure);
		var gateway = gateway();
		assertThatThrownBy(() -> gateway.analyze(input))
				.isExactlyInstanceOf(IncidentAnalysisTimeoutException.class)
				.hasMessage("Incident analysis provider timed out.").hasCause(failure);
		verify(model).call(any(Prompt.class));
	}

	@Test
	void translatesWrappedOkHttpCallDeadline() {
		var failure = new IllegalStateException(new OpenAIIoException("Request failed", new InterruptedIOException("timeout")));
		when(model.call(any(Prompt.class))).thenThrow(failure);
		var gateway = gateway();
		assertThatThrownBy(() -> gateway.analyze(input))
				.isExactlyInstanceOf(IncidentAnalysisTimeoutException.class).hasCause(failure);
		verify(model).call(any(Prompt.class));
	}

	@Test
	void ordinaryIoFailureRemainsGenericProviderFailure() {
		var failure = new OpenAIIoException("timeout in untrusted error text", new ConnectException("Connection refused"));
		when(model.call(any(Prompt.class))).thenThrow(failure);
		var gateway = gateway();
		assertThatThrownBy(() -> gateway.analyze(input))
				.isExactlyInstanceOf(IncidentAnalysisException.class).hasCause(failure);
	}

	@Test
	void ordinaryInterruptionIsNotClassifiedAsTimeout() {
		var failure = new OpenAIIoException("Request failed", new InterruptedIOException("interrupted"));
		when(model.call(any(Prompt.class))).thenThrow(failure);
		var gateway = gateway();
		assertThatThrownBy(() -> gateway.analyze(input))
				.isExactlyInstanceOf(IncidentAnalysisException.class).hasCause(failure);
	}

	private ChatResponse response(String content, ChatResponseMetadata metadata) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(content))), metadata);
	}
	@ParameterizedTest
	@MethodSource("dev.alexey.devassist.analysis.infrastructure.IncidentAnalysisConverterTests#malformedProviderJson")
	void rejectsInvalidProviderJsonWithoutRetry(String json) {
		when(model.call(any(Prompt.class))).thenReturn(response(json, ChatResponseMetadata.builder().build()));
		var gateway = gateway();
		assertThatThrownBy(() -> gateway.analyze(input)).isExactlyInstanceOf(IncidentAnalysisConversionException.class)
				.hasNoCause().hasMessageNotContaining("PRIVATE");
		verify(model).call(any(Prompt.class));
	}

	@ParameterizedTest
	@MethodSource("dev.alexey.devassist.analysis.infrastructure.IncidentAnalysisConverterTests#semanticallyInvalidJson")
	void rejectsSemanticViolationsBeforeReturning(String json) {
		when(model.call(any(Prompt.class))).thenReturn(response(json, ChatResponseMetadata.builder().build()));
		var gateway = gateway();
		assertThatThrownBy(() -> gateway.analyze(input)).isExactlyInstanceOf(IncidentAnalysisValidationException.class)
				.hasNoCause();
		verify(model).call(any(Prompt.class));
	}

}
