package dev.alexey.devassist.incident.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Validated investigation hypotheses; not an established root cause")
public record StructuredIncidentAnalysisDTO(
		@Schema(description = "Concise incident summary", minLength = 1, maxLength = 500, requiredMode = Schema.RequiredMode.REQUIRED)
		String summary,
		@ArraySchema(arraySchema = @Schema(description = "Prioritized hypotheses", requiredMode = Schema.RequiredMode.REQUIRED),
				minItems = 1, maxItems = 3, schema = @Schema(implementation = ProbableCauseDTO.class))
		List<ProbableCauseDTO> probableCauses,
		@ArraySchema(arraySchema = @Schema(description = "Ordered investigation plan", requiredMode = Schema.RequiredMode.REQUIRED),
				minItems = 1, maxItems = 4, schema = @Schema(implementation = InvestigationStepDTO.class))
		List<InvestigationStepDTO> investigationSteps,
		@ArraySchema(arraySchema = @Schema(description = "Missing evidence and assumptions", requiredMode = Schema.RequiredMode.REQUIRED),
				minItems = 1, maxItems = 3, schema = @Schema(type = "string", minLength = 1, maxLength = 250))
		List<String> uncertainties) {
	public StructuredIncidentAnalysisDTO {
		probableCauses = List.copyOf(probableCauses);
		investigationSteps = List.copyOf(investigationSteps);
		uncertainties = List.copyOf(uncertainties);
	}
}
