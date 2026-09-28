package dev.alexey.devassist.analysis.infrastructure;

import java.time.Duration;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hibernate.validator.constraints.time.DurationMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(@NotBlank String openAiModel, @NotBlank String promptVersion,
		@Min(1) @Max(16384) int maxOutputTokens,
		@NotNull @DurationMin(millis = 1) @DurationMax(millis = 2147483647) Duration timeout,
		@NotBlank @Pattern(regexp = "openai|ollama", message = "AI provider must be openai or ollama") String provider,
		@NotBlank String ollamaModel, @NotBlank String ollamaBaseUrl) {
}
