package dev.alexey.devassist.analysis.infrastructure;

import dev.alexey.devassist.analysis.IncidentAnalysisGateway;
import dev.alexey.devassist.analysis.IncidentAnalysisInput;
import dev.alexey.devassist.analysis.IncidentAnalysisResult;
import dev.alexey.devassist.analysis.exception.EmptyIncidentAnalysisException;
import dev.alexey.devassist.analysis.exception.IncidentAnalysisException;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;

/** Only an explicit analyze call uses the model. Framework types stay in this adapter. */
public final class OpenAiIncidentAnalysisGateway implements IncidentAnalysisGateway {

	private final ChatModel chatModel;
	private final String configuredModel;
	private final int maxOutputTokens;
	private final IncidentAnalysisPrompt prompt;
	private final Clock clock;
	private final LongSupplier nanoTime;

	public OpenAiIncidentAnalysisGateway(ChatModel chatModel, AiProperties properties,
			IncidentAnalysisPrompt prompt, Clock clock, LongSupplier nanoTime) {
		this.chatModel = chatModel;
		this.configuredModel = properties.model();
		this.maxOutputTokens = properties.maxOutputTokens();
		this.prompt = prompt;
		this.clock = clock;
		this.nanoTime = nanoTime;
	}

	@Override
	public IncidentAnalysisResult analyze(IncidentAnalysisInput incident) {
		long started = nanoTime.getAsLong();
		ChatResponse response;
		try {
			var request = new Prompt(List.of(new SystemMessage(prompt.system()), new UserMessage(prompt.user(incident))),
					OpenAiChatOptions.builder().model(configuredModel).maxCompletionTokens(maxOutputTokens).build());
			response = chatModel.call(request);
		}
		catch (RuntimeException exception) {
			throw new IncidentAnalysisException(exception);
		}
		long latencyMs = TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - started);
		if (response == null || response.getResult() == null) {
			throw new EmptyIncidentAnalysisException();
		}
		var output = response.getResult().getOutput();
		if (output == null) {
			throw new EmptyIncidentAnalysisException();
		}
		String content = output.getText();
		if (content == null || content.isBlank()) {
			throw new EmptyIncidentAnalysisException();
		}
		var metadata = response.getMetadata();
		var usage = metadata != null ? metadata.getUsage() : null;
		boolean hasUsage = usage != null && !(usage instanceof EmptyUsage);
		String model = metadata != null ? metadata.getModel() : null;
		return new IncidentAnalysisResult(content, "openai",
				model == null || model.isBlank() ? configuredModel : model,
				prompt.version(), clock.instant(), latencyMs,
				hasUsage ? usage.getPromptTokens() : null,
				hasUsage ? usage.getCompletionTokens() : null,
				hasUsage ? usage.getTotalTokens() : null);
	}
}
