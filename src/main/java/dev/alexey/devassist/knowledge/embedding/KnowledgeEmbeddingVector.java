package dev.alexey.devassist.knowledge.embedding;

public record KnowledgeEmbeddingVector(float[] values) {
    public KnowledgeEmbeddingVector {
        if (values == null || values.length != 768) {
            throw new IllegalArgumentException("Embedding vector must contain 768 values");
        }
        for (float value : values) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Embedding vector must be finite");
        }
        values = values.clone();
    }

    @Override
    public float[] values() {
        return values.clone();
    }
}
