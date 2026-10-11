package dev.alexey.devassist.knowledge.embedding;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KnowledgeEmbeddingVectorTests {
    @Test void equalityHashAndDescriptionUseContentWithoutExposingValues() {
        float[] input = vector(0.25f);
        var first = new KnowledgeEmbeddingVector(input);
        var same = new KnowledgeEmbeddingVector(vector(0.25f));
        var different = new KnowledgeEmbeddingVector(vector(0.5f));
        input[0] = 9f;
        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(different);
        assertThat(first.toString()).isEqualTo(same.toString()).isNotEqualTo(different.toString())
                .contains("dimensions=768").doesNotContain("0.25", "0.5");
        float[] exposed = first.values();
        exposed[0] = 9f;
        assertThat(first).isEqualTo(same);
    }

    private float[] vector(float value) {
        float[] values = new float[768];
        values[0] = value;
        return values;
    }
}
