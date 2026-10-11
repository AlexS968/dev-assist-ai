package dev.alexey.devassist.knowledge.embedding.exception;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KnowledgeEmbeddingExceptionSerializationTests {
    @Test void safeDiagnosticSurvivesExceptionSerialization() throws Exception {
        var diagnostic = new KnowledgeEmbeddingDiagnostic("openai",
                KnowledgeEmbeddingDiagnostic.Category.HTTP_ERROR, 429, "rate_limit_exceeded");
        var original = new KnowledgeEmbeddingException("Embedding provider unavailable", diagnostic);
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        KnowledgeEmbeddingException restored;
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (KnowledgeEmbeddingException) input.readObject();
        }
        assertThat(restored.getMessage()).isEqualTo("Embedding provider unavailable");
        assertThat(restored.diagnostic()).isEqualTo(diagnostic);
    }
}
