package dev.alexey.devassist.ragevaluation;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;

/** Test-only live settings; the explicit selector controls both gateways. */
final class LiveRagProviderConfiguration {
    static final String OPENAI_BASE_URL = "https://api.openai.com/v1";

    private LiveRagProviderConfiguration() {}

    static void register(Map<String, String> environment, DynamicPropertyRegistry registry) {
        resolve(environment).forEach((name, value) -> registry.add(name, () -> value));
    }

    static Map<String, String> resolve(Map<String, String> environment) {
        String provider = environment.get("RAG_LIVE_PROVIDER");
        if (provider == null || provider.isBlank()) {
            throw new IllegalStateException("RAG_LIVE_PROVIDER must be openai or ollama");
        }
        if (!provider.equals("openai") && !provider.equals("ollama")) {
            throw new IllegalStateException("Unsupported RAG_LIVE_PROVIDER; use openai or ollama");
        }
        if (provider.equals("openai") && isBlank(environment.get("OPENAI_API_KEY"))) {
            throw new IllegalStateException("OPENAI_API_KEY is required for OpenAI live evaluation");
        }
        requireCompatible(environment, "RAG_ENABLED", "true");
        requireCompatible(environment, "AI_PROMPT_VERSION", "incident-analysis-v3");
        requireCompatible(environment, "RAG_EMBEDDING_DIMENSIONS", "768");
        requireCompatible(environment, "AI_MAX_OUTPUT_TOKENS", "1000");
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("app.rag.enabled", "true");
        properties.put("app.ai.provider", provider);
        properties.put("app.rag.embedding.provider", provider);
        properties.put("app.ai.prompt-version", "incident-analysis-v3");
        properties.put("app.rag.embedding.dimensions", "768");
        properties.put("app.ai.max-output-tokens", "1000");
        properties.put("app.ai.timeout", valueOrDefault(environment, "AI_TIMEOUT", "60s"));
        properties.put("app.rag.embedding.timeout",
                valueOrDefault(environment, "RAG_EMBEDDING_TIMEOUT", "60s"));
        if (provider.equals("openai")) {
            properties.put("spring.ai.openai.api-key", environment.get("OPENAI_API_KEY"));
            properties.put("spring.ai.openai.base-url", OPENAI_BASE_URL);
        }
        return Map.copyOf(properties);
    }

    private static void requireCompatible(Map<String, String> environment, String name, String expected) {
        String actual = environment.get(name);
        if (actual != null && !actual.equals(expected)) {
            throw new IllegalStateException(name + " must be " + expected + " for live evaluation");
        }
    }

    private static String valueOrDefault(Map<String, String> environment, String name, String fallback) {
        String value = environment.get(name);
        return isBlank(value) ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
