package dev.alexey.devassist.analysis.infrastructure;

import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentAnalysisSchemaTests {
	private final IncidentAnalysisSchema schema = new IncidentAnalysisSchema();

	@Test
	void canonicalSchemaRequiresExactlyTheContractAtEveryObjectLevel() {
		var root = JsonMapper.shared().readTree(schema.json());
		assertObject(root, "summary", "probableCauses", "investigationSteps", "uncertainties");
		assertThat(root.at("/properties/summary/type").asString()).isEqualTo("string");
		var cause = root.at("/properties/probableCauses/items");
		assertObject(cause, "title", "explanation", "likelihood", "evidenceToCheck");
		assertThat(cause.at("/properties/likelihood/enum").values()).extracting(JsonNode::asString).containsExactly("LOW", "MEDIUM", "HIGH");
		assertThat(cause.at("/properties/title/type").asString()).isEqualTo("string");
		assertThat(cause.at("/properties/explanation/type").asString()).isEqualTo("string");
		assertThat(cause.at("/properties/evidenceToCheck/items/type").asString()).isEqualTo("string");
		var step = root.at("/properties/investigationSteps/items");
		assertObject(step, "order", "action", "rationale");
		assertThat(step.at("/properties/order/type").asString()).isEqualTo("integer");
		assertThat(step.at("/properties/action/type").asString()).isEqualTo("string");
		assertThat(step.at("/properties/rationale/type").asString()).isEqualTo("string");
		assertThat(root.at("/properties/uncertainties/items/type").asString()).isEqualTo("string");
		JsonNode providerSchema = JsonMapper.shared().valueToTree(schema.asMap());
		assertThat(providerSchema).isEqualTo(root);
	}

	@Test
	void providerMapsCannotModifyCanonicalSchema() {
		var providerSchema = schema.asMap();
		providerSchema.clear();
		assertThat(schema.asMap()).containsKeys("type", "properties", "required", "additionalProperties");
	}

	private void assertObject(JsonNode node, String... fields) {
		assertThat(node.path("type").asString()).isEqualTo("object");
		assertThat(node.path("additionalProperties").asBoolean()).isFalse();
		assertThat(node.path("properties").propertyNames()).containsExactlyInAnyOrderElementsOf(Set.of(fields));
		assertThat(node.path("required").values()).extracting(JsonNode::asString).containsExactlyInAnyOrder(fields);
	}
}
