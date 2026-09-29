package dev.alexey.devassist.analysis.infrastructure;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** One versioned schema for both native provider requests; business limits remain in the validator. */
public final class IncidentAnalysisSchema {
	private final String json;

	public IncidentAnalysisSchema() {
		try {
			json = new ClassPathResource("schemas/incident-analysis-v2.json")
					.getContentAsString(StandardCharsets.UTF_8).strip();
		}
		catch (IOException exception) {
			throw new IllegalStateException("Cannot load incident analysis schema.", exception);
		}
	}

	public String json() {
		return json;
	}

	/** A fresh map prevents a provider from modifying the canonical schema. */
	public Map<String, Object> asMap() {
		return JsonMapper.shared().readValue(json, new TypeReference<Map<String, Object>>() { });
	}
}
