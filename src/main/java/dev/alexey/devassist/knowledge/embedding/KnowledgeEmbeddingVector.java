package dev.alexey.devassist.knowledge.embedding;

import java.util.Arrays;

public record KnowledgeEmbeddingVector(float[] values) {
    public KnowledgeEmbeddingVector {
        if (values == null || values.length != 768) {
            throw new IllegalArgumentException("Embedding vector must contain 768 values");
        }
        for (float value : values) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Embedding vector must be finite");
        }
        validateNorm(values);
        values = values.clone();
    }

    public static void validateNorm(float[] values) {
        if (values == null || values.length != 768) {
            throw new IllegalArgumentException("Embedding vector must contain 768 values");
        }
        double scale = 0;
        for (float value : values) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Embedding vector must be finite");
            scale = Math.max(scale, Math.abs((double) value));
        }
        if (scale == 0) throw new IllegalArgumentException("Embedding vector must be nonzero");
        double sum = 0;
        for (float value : values) {
            double normalized = value / scale;
            sum += normalized * normalized;
        }
        if (!Double.isFinite(scale * Math.sqrt(sum))) {
            throw new IllegalArgumentException("Embedding vector norm must be finite");
        }
    }

    @Override
    public float[] values() {
        return values.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof KnowledgeEmbeddingVector(float[] otherValues)
                && Arrays.equals(values, otherValues);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return "KnowledgeEmbeddingVector[dimensions=" + values.length
                + ", contentHash=" + Integer.toHexString(hashCode()) + "]";
    }
}
