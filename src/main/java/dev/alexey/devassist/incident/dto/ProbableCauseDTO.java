package dev.alexey.devassist.incident.dto;

import dev.alexey.devassist.analysis.enums.Likelihood;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "An unverified hypothesis and evidence to check")
public record ProbableCauseDTO(
		@Schema(description = "Hypothesis title", minLength = 1, maxLength = 120, requiredMode = Schema.RequiredMode.REQUIRED)
		String title,
		@Schema(description = "Concise explanation of the hypothesis", minLength = 1, maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED)
		String explanation,
		@Schema(description = "Qualitative prioritization by the model, not probability or measured confidence", requiredMode = Schema.RequiredMode.REQUIRED)
		Likelihood likelihood,
		@ArraySchema(arraySchema = @Schema(description = "Evidence to confirm or refute the hypothesis", requiredMode = Schema.RequiredMode.REQUIRED),
				minItems = 1, maxItems = 3, schema = @Schema(type = "string", minLength = 1, maxLength = 250))
		List<String> evidenceToCheck) {
	public ProbableCauseDTO {
		evidenceToCheck = List.copyOf(evidenceToCheck);
	}
}
