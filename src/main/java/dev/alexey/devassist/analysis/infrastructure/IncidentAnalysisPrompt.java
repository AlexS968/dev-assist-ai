package dev.alexey.devassist.analysis.infrastructure;

import dev.alexey.devassist.analysis.IncidentAnalysisInput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

/** Loads one immutable prompt version at startup. Incident data is rendered only into the user message. */
public final class IncidentAnalysisPrompt {

	private static final String REPAIR_INSTRUCTION = "The previous response failed structural or semantic validation. "
			+ "Generate a new complete answer strictly matching the supplied JSON schema and all stated constraints. "
			+ "Return only the complete JSON object.";

	private final String version;
	private final String system;
	private final PromptTemplate user;

	public IncidentAnalysisPrompt(String version) {
		String directory = switch (version) {
			case "incident-analysis-v2" -> "prompts/incident-analysis/v2/";
			default -> throw new IllegalArgumentException("Unsupported or incompatible incident analysis prompt version: " + version);
		};
		this.version = version;
		this.system = read(directory + "system.st");
		this.user = new PromptTemplate(read(directory + "user.st"));
	}

	public String version() {
		return version;
	}

	public String system() {
		return system;
	}

	public String system(IncidentAnalysisInput incident) {
		return incident.repair() ? system + "\n" + REPAIR_INSTRUCTION : system;
	}

	public String user(IncidentAnalysisInput incident) {
		return user.render(Map.of("title", incident.title(), "description", incident.description()));
	}

	private static String read(String path) {
		try {
			return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8).strip();
		}
		catch (IOException exception) {
			throw new IllegalStateException("Cannot load incident analysis prompt resource: " + path, exception);
		}
	}
}
