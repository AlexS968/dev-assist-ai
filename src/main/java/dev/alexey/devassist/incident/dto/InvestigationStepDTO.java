package dev.alexey.devassist.incident.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A concrete investigation step")
public record InvestigationStepDTO(
		@Schema(description = "Unique consecutive position in the plan, 1..N", minimum = "1", maximum = "4", requiredMode = Schema.RequiredMode.REQUIRED)
		int order,
		@Schema(description = "Action to take", minLength = 1, maxLength = 300, requiredMode = Schema.RequiredMode.REQUIRED)
		String action,
		@Schema(description = "Concise reason this check is useful", minLength = 1, maxLength = 300, requiredMode = Schema.RequiredMode.REQUIRED)
		String rationale) {
}
