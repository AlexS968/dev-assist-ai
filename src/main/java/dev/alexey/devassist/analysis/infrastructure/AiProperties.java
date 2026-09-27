package dev.alexey.devassist.analysis.infrastructure;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(@NotBlank String model, @NotBlank String promptVersion,
		@Min(1) @Max(16384) int maxOutputTokens) {
}
