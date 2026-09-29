package dev.alexey.devassist.analysis.infrastructure;

import dev.alexey.devassist.analysis.IncidentAnalysisGateway;
import dev.alexey.devassist.analysis.IncidentAnalysisInput;
import dev.alexey.devassist.analysis.IncidentAnalysisResult;
import dev.alexey.devassist.analysis.IncidentAnalysisAttemptUsage;
import dev.alexey.devassist.analysis.exception.EmptyIncidentAnalysisException;
import dev.alexey.devassist.analysis.exception.IncidentAnalysisException;
import dev.alexey.devassist.analysis.exception.IncidentAnalysisTimeoutException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
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
	private final IncidentAnalysisConverter converter;
	private final IncidentAnalysisSchema schema;

	public OpenAiIncidentAnalysisGateway(ChatModel chatModel, AiProperties properties,
			IncidentAnalysisPrompt prompt, Clock clock, LongSupplier nanoTime,
			IncidentAnalysisConverter converter, IncidentAnalysisSchema schema) {
		this.chatModel = chatModel;
		this.configuredModel = properties.openAiModel();
		this.maxOutputTokens = properties.maxOutputTokens();
		this.prompt = prompt;
		this.clock = clock;
		this.nanoTime = nanoTime;
		this.converter = converter;
		this.schema = schema;
	}

	@Override
	public IncidentAnalysisResult analyze(IncidentAnalysisInput incident) {
		long started = nanoTime.getAsLong();
		ChatResponse response;
		try {
			var request = new Prompt(List.of(new SystemMessage(prompt.system(incident)), new UserMessage(prompt.user(incident))),
					OpenAiChatOptions.builder().model(configuredModel).maxCompletionTokens(maxOutputTokens)
						.responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.JSON_SCHEMA)
								.jsonSchema(schema.json()).strict(true).build()).build());
			response = chatModel.call(request);
		}
		catch (RuntimeException exception) {
			if (isTransportTimeout(exception)) {
				throw new IncidentAnalysisTimeoutException(exception);
			}
			throw new IncidentAnalysisException(exception);
		}
		long latencyMs = TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - started);
		return toAnalysisResult(response, latencyMs);
	}

	private IncidentAnalysisResult toAnalysisResult(ChatResponse response, long latencyMs) {
		if (response.getResult() == null) {
			throw new EmptyIncidentAnalysisException();
		}
		var output = response.getResult().getOutput();
		String content = output.getText();
		if (content == null || content.isBlank()) {
			throw new EmptyIncidentAnalysisException();
		}
		var metadata = response.getMetadata();
		var usage = metadata.getUsage();
		boolean hasUsage = !(usage instanceof EmptyUsage);
		String model = metadata.getModel();
		var attemptUsage = new IncidentAnalysisAttemptUsage(hasUsage ? usage.getPromptTokens() : null,
				hasUsage ? usage.getCompletionTokens() : null, hasUsage ? usage.getTotalTokens() : null);
		return new IncidentAnalysisResult(converter.convert(content, attemptUsage), "openai",
				model.isBlank() ? configuredModel : model,
				prompt.version(), clock.instant(), latencyMs,
				attemptUsage.inputTokens(), attemptUsage.outputTokens(), attemptUsage.totalTokens(), 1);
	}

	private static boolean isTransportTimeout(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			// OkHttp read/connect timeouts use SocketTimeoutException. Its whole-call
			// deadline uses InterruptedIOException("timeout"); ordinary interruption
			// must not be mistaken for a timeout.
			if (cause instanceof SocketTimeoutException
					|| (cause instanceof InterruptedIOException && "timeout".equals(cause.getMessage()))) {
				return true;
			}
		}
		return false;
	}
}
