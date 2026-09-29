package dev.alexey.devassist.analysis.infrastructure;

import dev.alexey.devassist.analysis.exception.IncidentAnalysisConversionException;
import dev.alexey.devassist.analysis.model.StructuredIncidentAnalysis;
import dev.alexey.devassist.analysis.validation.IncidentAnalysisValidator;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

/** Dedicated strict reader; never changes the REST mapper or retains Jackson diagnostics. */
public final class IncidentAnalysisConverter {
	private final IncidentAnalysisValidator validator;
	private final ObjectReader reader = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
					DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
					DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
					DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES,
					DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
			.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
			.disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
			.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
			.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
			.withCoercionConfig(LogicalType.Enum, config -> config
					.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail))
			.withCoercionConfig(LogicalType.Textual, config -> config
					.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
					.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
					.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
			.build().readerFor(StructuredIncidentAnalysis.class);

	public IncidentAnalysisConverter(IncidentAnalysisValidator validator) {
		this.validator = validator;
	}

	public StructuredIncidentAnalysis convert(String content) {
		if (content == null) {
			throw new IncidentAnalysisConversionException();
		}
		StructuredIncidentAnalysis analysis;
		try {
			analysis = reader.readValue(content);
		}
		catch (JacksonException exception) {
			throw new IncidentAnalysisConversionException();
		}
		validator.validate(analysis);
		return analysis;
	}
}
