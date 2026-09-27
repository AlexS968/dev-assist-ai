package dev.alexey.devassist.analysis.infrastructure;

import dev.alexey.devassist.analysis.IncidentAnalysisInput;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiIncidentAnalysisGatewayTests {

	@Test
	void constructionDoesNotCallModel() {
		ChatModel model = mock(ChatModel.class);

		new OpenAiIncidentAnalysisGateway(model);

		verify(model, never()).call(any(Prompt.class));
	}

	@Test
	void passesIncidentAsUserDataAndReturnsPlainText() {
		ChatModel model = mock(ChatModel.class);
		when(model.getOptions()).thenReturn(ChatOptions.builder().build());
		when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
				new Generation(new AssistantMessage("Check database connectivity.")))));
		var gateway = new OpenAiIncidentAnalysisGateway(model);
		var incident = new IncidentAnalysisInput("Database unavailable", "Request failed: {\"code\":503}");

		assertThat(gateway.analyze(incident)).isEqualTo("Check database connectivity.");

		var prompt = ArgumentCaptor.forClass(Prompt.class);
		verify(model).call(prompt.capture());
		assertThat(prompt.getValue().getInstructions()).hasSize(2);
		assertThat(prompt.getValue().getInstructions().getFirst().getMessageType())
				.isEqualTo(MessageType.SYSTEM);
		assertThat(prompt.getValue().getInstructions().getLast().getMessageType())
				.isEqualTo(MessageType.USER);
		assertThat(prompt.getValue().getInstructions().getLast().getText())
				.isEqualTo("Title: Database unavailable\nDescription: Request failed: {\"code\":503}");
	}
}
