package dev.alexey.devassist.analysis.infrastructure;

import dev.alexey.devassist.analysis.IncidentAnalysisGateway;
import dev.alexey.devassist.analysis.IncidentAnalysisInput;
import dev.alexey.devassist.analysis.IncidentAnalysisResult;
import dev.alexey.devassist.analysis.IncidentAnalysisAttemptUsage;
import dev.alexey.devassist.analysis.exception.EmptyIncidentAnalysisException;
import dev.alexey.devassist.analysis.exception.IncidentAnalysisException;
import dev.alexey.devassist.analysis.exception.IncidentAnalysisTimeoutException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;

/** Uses the official low-level API: no model retries, model pulls, or zero-filled usage. */
public final class OllamaIncidentAnalysisGateway implements IncidentAnalysisGateway {

	private final OllamaApi api;
	private final AiProperties properties;
	private final IncidentAnalysisPrompt prompt;
	private final Clock clock;
	private final LongSupplier nanoTime;
	private final IncidentAnalysisConverter converter;
	private final IncidentAnalysisSchema schema;

	public OllamaIncidentAnalysisGateway(OllamaApi api, AiProperties properties,
			IncidentAnalysisPrompt prompt, Clock clock, LongSupplier nanoTime,
			IncidentAnalysisConverter converter, IncidentAnalysisSchema schema) {
		this.api = api;
		this.properties = properties;
		this.prompt = prompt;
		this.clock = clock;
		this.nanoTime = nanoTime;
		this.converter = converter;
		this.schema = schema;
	}

	@Override
	public IncidentAnalysisResult analyze(IncidentAnalysisInput incident) {
		var options = OllamaChatOptions.builder().numPredict(properties.maxOutputTokens()).disableThinking().build();
		var request = OllamaApi.ChatRequest.builder(properties.ollamaModel()).stream(false)
				.messages(List.of(OllamaApi.Message.builder(OllamaApi.Message.Role.SYSTEM).content(prompt.system(incident)).build(),
						OllamaApi.Message.builder(OllamaApi.Message.Role.USER).content(prompt.user(incident)).build()))
				.options(options).think(options.getThinkOption()).format(schema.asMap()).build();
		long started = nanoTime.getAsLong();
		OllamaApi.ChatResponse response;
		try {
			response = api.chat(request);
		}
		catch (RuntimeException exception) {
			for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
				if (cause instanceof SocketTimeoutException) {
					throw new IncidentAnalysisTimeoutException(exception);
				}
			}
			throw new IncidentAnalysisException(exception);
		}
		long latency = TimeUnit.NANOSECONDS.toMillis(nanoTime.getAsLong() - started);
		if (response.message().content() == null || response.message().content().isBlank()) {
			throw new EmptyIncidentAnalysisException();
		}
		String model = response.model();
		// Ollama does not report a total count; do not manufacture one.
		var attemptUsage = new IncidentAnalysisAttemptUsage(response.promptEvalCount(), response.evalCount(), null);
		return new IncidentAnalysisResult(converter.convert(response.message().content(), attemptUsage), "ollama",
				model.isBlank() ? properties.ollamaModel() : model,
				prompt.version(), clock.instant(), latency, response.promptEvalCount(), response.evalCount(), null, 1);
	}
}
