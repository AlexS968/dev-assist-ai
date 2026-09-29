package dev.alexey.devassist.analysis.infrastructure;

import dev.alexey.devassist.analysis.exception.IncidentAnalysisConversionException;
import dev.alexey.devassist.analysis.exception.IncidentAnalysisValidationException;
import dev.alexey.devassist.analysis.validation.IncidentAnalysisValidator;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static dev.alexey.devassist.analysis.StructuredAnalysisFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentAnalysisConverterTests {
	private final IncidentAnalysisValidator validator = spy(new IncidentAnalysisValidator());
	private final IncidentAnalysisConverter converter = new IncidentAnalysisConverter(validator);

	@Test
	void convertsAndValidatesBeforeReturning() {
		assertThat(converter.convert(JSON)).isEqualTo(analysis());
		verify(validator).validate(analysis());
	}

	@ParameterizedTest
	@MethodSource("invalidJson")
	void rejectsInvalidJsonWithoutRetainingRawData(String json) {
		var exception = catchThrowableOfType(IncidentAnalysisConversionException.class, () -> converter.convert(json));
		assertThat(exception).hasMessage("Incident analysis response could not be converted.").hasNoCause();
		var trace = new StringWriter();
		exception.printStackTrace(new PrintWriter(trace));
		assertThat(trace.toString()).doesNotContain("PRIVATE", "Database timeouts", "Connection exhaustion");
		verifyNoInteractions(validator);
	}

	static Stream<String> malformedProviderJson() {
		return invalidJson().filter(json -> json != null && !json.isBlank());
	}

	static Stream<String> invalidJson() {
		return Stream.of(null, "", "PRIVATE not JSON", "{", JSON + " PRIVATE trailing text", JSON + " {}",
				"```json\n" + JSON + "```", JSON.replace("{", "{/*PRIVATE*/"),
				JSON.replace("\"summary\":", "\"PRIVATE\": true, \"summary\":"),
				JSON.replace("\"title\":", "\"PRIVATE\": true, \"title\":"),
				JSON.replace("\"order\":", "\"PRIVATE\": true, \"order\":"),
				JSON.replace("MEDIUM", "PRIVATE"), JSON.replace("MEDIUM", "medium"),
				JSON.replace("\"MEDIUM\"", "1"), JSON.replace("\"MEDIUM\"", "\"1\""),
				JSON.replace("\"Database timeouts\"", "123"), JSON.replace("\"Database timeouts\"", "true"),
				JSON.replace("\"Database timeouts\"", "1.5"),
				JSON.replace("\"order\": 1", "\"order\": \"1\""),
				JSON.replace("\"order\": 1", "\"order\": 1.5"),
				JSON.replace("\"order\": 1", "\"order\": null"),
				JSON.replace("\"order\": 1", "\"order\": 2147483648"),
				JSON.replace("\"summary\":", "\"summary\": \"PRIVATE\", \"summary\":"));
	}

	@ParameterizedTest
	@MethodSource("missingOrNullFields")
	void rejectsEveryMissingOrNullRequiredField(String json) {
		assertThatThrownBy(() -> converter.convert(json)).isExactlyInstanceOf(IncidentAnalysisConversionException.class);
	}

	static Stream<String> missingOrNullFields() {
		return Stream.of("/summary", "/probableCauses", "/investigationSteps", "/uncertainties",
				"/probableCauses/0/title", "/probableCauses/0/explanation", "/probableCauses/0/likelihood",
				"/probableCauses/0/evidenceToCheck", "/investigationSteps/0/order", "/investigationSteps/0/action",
				"/investigationSteps/0/rationale").flatMap(path -> {
			var root = JsonMapper.shared().readTree(JSON);
			int separator = path.lastIndexOf('/');
			var parent = (ObjectNode) root.at(path.substring(0, separator));
			String field = path.substring(separator + 1);
			parent.remove(field);
			String missing = root.toString();
			parent.putNull(field);
			return Stream.of(missing, root.toString());
		});
	}

	@ParameterizedTest
	@MethodSource("semanticallyInvalidJson")
	void semanticFailureRemainsDistinctFromConversionFailure(String json) {
		assertThatThrownBy(() -> converter.convert(json)).isExactlyInstanceOf(IncidentAnalysisValidationException.class)
				.hasNoCause();
		verify(validator).validate(any());
	}

	static Stream<String> semanticallyInvalidJson() {
		return Stream.of("null", JSON.replace("Database timeouts", " "),
				JSON.replace("\"order\": 1", "\"order\": 0"),
				JSON.replace("Metrics are unavailable", "x".repeat(251)),
				JSON.replace("[\"Check active connections\"]", "[]"),
				JSON.replace("[\"Check active connections\"]", "[null]"));
	}
}
