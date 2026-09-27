package dev.alexey.devassist.analysis.infrastructure;

import dev.alexey.devassist.analysis.IncidentAnalysisInput;
import dev.alexey.devassist.analysis.exception.EmptyIncidentAnalysisException;
import dev.alexey.devassist.analysis.exception.IncidentAnalysisException;
import java.time.Clock;
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
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpenAiIncidentAnalysisGatewayTests {

	private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");
	private final ChatModel model = mock(ChatModel.class);
	private final IncidentAnalysisInput input = new IncidentAnalysisInput("Database unavailable", "Request failed: {\"code\":503}");

	private OpenAiIncidentAnalysisGateway gateway() {
		when(model.getOptions()).thenReturn(ChatOptions.builder().build());
		var ticks = new AtomicLong(100_000_000L);
		return new OpenAiIncidentAnalysisGateway(model, "configured-model",
				Clock.fixed(NOW, ZoneOffset.UTC), () -> ticks.getAndAdd(125_000_000L));
	}

	@Test
	void constructionDoesNotCallModel() {
		gateway();
		verify(model, never()).call(any(Prompt.class));
	}

	@Test
	void mapsContentMetadataAndMeasuredTime() {
		when(model.call(any(Prompt.class))).thenReturn(response("Check database connectivity.",
				ChatResponseMetadata.builder().model("reported-model").usage(new DefaultUsage(120, 80, 200)).build()));

		var result = gateway().analyze(input);

		assertThat(result.content()).isEqualTo("Check database connectivity.");
		assertThat(result.provider()).isEqualTo("openai");
		assertThat(result.model()).isEqualTo("reported-model");
		assertThat(result.generatedAt()).isEqualTo(NOW);
		assertThat(result.latencyMs()).isEqualTo(125);
		assertThat(result.inputTokens()).isEqualTo(120);
		assertThat(result.outputTokens()).isEqualTo(80);
		assertThat(result.totalTokens()).isEqualTo(200);
		var prompt = ArgumentCaptor.forClass(Prompt.class);
		verify(model).call(prompt.capture());
		assertThat(prompt.getValue().getInstructions()).hasSize(2);
		assertThat(prompt.getValue().getInstructions().getFirst().getMessageType()).isEqualTo(MessageType.SYSTEM);
		assertThat(prompt.getValue().getInstructions().getLast().getMessageType()).isEqualTo(MessageType.USER);
		assertThat(prompt.getValue().getInstructions().getLast().getText())
				.isEqualTo("Title: Database unavailable\nDescription: Request failed: {\"code\":503}");
	}

	@Test
	void missingUsageStaysNullAndMissingModelUsesConfiguredModel() {
		when(model.call(any(Prompt.class))).thenReturn(response("Analysis", ChatResponseMetadata.builder().build()));
		var result = gateway().analyze(input);
		assertThat(result.model()).isEqualTo("configured-model");
		assertThat(result.inputTokens()).isNull();
		assertThat(result.outputTokens()).isNull();
		assertThat(result.totalTokens()).isNull();
	}

	@Test
	void preservesActuallyReportedZeroTokens() {
		when(model.call(any(Prompt.class))).thenReturn(response("Analysis",
				ChatResponseMetadata.builder().usage(new DefaultUsage(0, 0, 0)).build()));
		var result = gateway().analyze(input);
		assertThat(result.inputTokens()).isZero();
		assertThat(result.outputTokens()).isZero();
		assertThat(result.totalTokens()).isZero();
	}

	@Test
	void preservesPartiallyMissingUsageWithoutCalculatingTotal() {
		Usage usage = mock(Usage.class);
		when(usage.getPromptTokens()).thenReturn(12);
		when(usage.getCompletionTokens()).thenReturn(null);
		when(usage.getTotalTokens()).thenReturn(null);
		when(model.call(any(Prompt.class))).thenReturn(response("Analysis",
				ChatResponseMetadata.builder().usage(usage).build()));
		var result = gateway().analyze(input);
		assertThat(result.inputTokens()).isEqualTo(12);
		assertThat(result.outputTokens()).isNull();
		assertThat(result.totalTokens()).isNull();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\n\t"})
	void rejectsEmptyContent(String content) {
		when(model.call(any(Prompt.class))).thenReturn(response(content, ChatResponseMetadata.builder().build()));
		assertThatThrownBy(() -> gateway().analyze(input)).isInstanceOf(EmptyIncidentAnalysisException.class);
	}

	@Test
	void rejectsNullOutputAsEmptyResponse() {
		when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(null))));

		assertThatThrownBy(() -> gateway().analyze(input))
				.isExactlyInstanceOf(EmptyIncidentAnalysisException.class)
				.hasNoCause();
	}

	@Test
	void absentMetadataUsesConfiguredModelAndNullTokenCounts() {
		// Spring AI 2.0.1 normalizes null constructor metadata to empty metadata.
		// Exercise that supported path without reflection or overriding its contract.
		var response = response("Analysis", null);
		assertThat(response.getMetadata()).isNotNull();
		when(model.call(any(Prompt.class))).thenReturn(response);

		var result = gateway().analyze(input);

		assertThat(result.content()).isEqualTo("Analysis");
		assertThat(result.provider()).isEqualTo("openai");
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
		assertThatThrownBy(() -> gateway().analyze(input)).isInstanceOf(EmptyIncidentAnalysisException.class);
	}

	@Test
	void rejectsNullResponse() {
		when(model.call(any(Prompt.class))).thenReturn(null);
		assertThatThrownBy(() -> gateway().analyze(input)).isInstanceOf(EmptyIncidentAnalysisException.class);
	}

	@Test
	void translatesProviderFailureWithoutRetry() {
		var failure = new IllegalStateException("fake-key and private prompt");
		when(model.call(any(Prompt.class))).thenThrow(failure);
		assertThatThrownBy(() -> gateway().analyze(input))
				.isInstanceOf(IncidentAnalysisException.class)
				.hasMessage("Incident analysis provider failed.").hasCause(failure);
		verify(model).call(any(Prompt.class));
	}

	private ChatResponse response(String content, ChatResponseMetadata metadata) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(content))), metadata);
	}
}
