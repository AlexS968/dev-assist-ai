package dev.alexey.devassist.knowledge.embedding;

public record EmbeddingProfile(String provider, String model, int dimensions) {
    public EmbeddingProfile {
        if (provider == null || provider.isBlank() || model == null || model.isBlank()
                || dimensions != 768 || provider.length() > 50 || model.length() > 255) {
            throw new IllegalArgumentException("Invalid embedding profile");
        }
    }

    public boolean matches(String storedProvider, String storedModel, Integer storedDimensions) {
        return provider.equals(storedProvider) && model.equals(storedModel)
                && storedDimensions != null && dimensions == storedDimensions;
    }
}
