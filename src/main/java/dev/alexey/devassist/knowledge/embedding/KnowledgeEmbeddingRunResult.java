package dev.alexey.devassist.knowledge.embedding;

import java.util.UUID;

public record KnowledgeEmbeddingRunResult(UUID knowledgeBaseId, EmbeddingProfile activeProfile,
        int discoveredCount, int attemptedCount, int embeddedCount, int skippedMissingCount,
        int skippedContentChangedCount, int skippedAlreadyCompatibleCount,
        int skippedConcurrencyConflictCount, int batchCount, Long inputTokens,
        long providerLatencyMs, long totalRunLatencyMs, boolean limitReached) {}
