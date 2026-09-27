package dev.alexey.devassist.analysis.infrastructure;

import dev.alexey.devassist.analysis.IncidentAnalysisInput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;

/** Loads one immutable prompt version at startup. Incident data is rendered only into the user message. */
public final class IncidentAnalysisPrompt {

	private final String version;
	private final String system;
	private final PromptTemplate user;

	public IncidentAnalysisPrompt(String version) {
		String directory = switch (version) {
			case "incident-analysis-v1" -> "prompts/incident-analysis/v1/";
			default -> throw new IllegalArgumentException("Unsupported incident analysis prompt version: " + version);
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
