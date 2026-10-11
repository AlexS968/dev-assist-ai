package dev.alexey.devassist.analysis.grounding;

import dev.alexey.devassist.knowledge.retrieval.KnowledgeRetrievalRequest;
import dev.alexey.devassist.knowledge.retrieval.KnowledgeRetrievalResult;
import dev.alexey.devassist.knowledge.retrieval.KnowledgeRetrievalService;
import dev.alexey.devassist.knowledge.embedding.exception.KnowledgeEmbeddingException;
import dev.alexey.devassist.knowledge.exception.KnowledgeBaseNotFoundException;
import jakarta.persistence.PersistenceException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

/** Retrieval is optional and never holds a database transaction during provider calls. */
public final class IncidentAnalysisGroundingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(IncidentAnalysisGroundingService.class);
    private final KnowledgeRetrievalService retrieval;
    private final GroundingProperties limits;
    private final boolean enabled;
    private final IncidentAnalysisContextSelector selector = new IncidentAnalysisContextSelector();

    public IncidentAnalysisGroundingService(KnowledgeRetrievalService retrieval,
            GroundingProperties limits, boolean enabled) {
        this.retrieval = retrieval;
        this.limits = limits;
        this.enabled = enabled;
    }

    public GroundingDecision prepare(UUID knowledgeBaseId, String title, String description) {
        if (!enabled) return GroundingDecision.withoutRetrieval(IncidentGroundingStatus.RAG_DISABLED, knowledgeBaseId);
        if (knowledgeBaseId == null) {
            return GroundingDecision.withoutRetrieval(IncidentGroundingStatus.NO_KNOWLEDGE_BASE, null);
        }
        String query = title + "\n" + description;
        if (query.length() > 10000) {
            int end = Character.isHighSurrogate(query.charAt(9999)) ? 9999 : 10000;
            query = query.substring(0, end);
        }
        var request = new KnowledgeRetrievalRequest(knowledgeBaseId, query, null, null);
        long start = System.nanoTime();
        KnowledgeRetrievalResult result;
        try {
            result = retrieval.retrieve(request);
        }
        catch (KnowledgeEmbeddingException failure) {
            return failed(knowledgeBaseId, start, "embedding");
        }
        catch (DataAccessException | PersistenceException failure) {
            return failed(knowledgeBaseId, start, "database");
        }
        catch (KnowledgeBaseNotFoundException failure) {
            return failed(knowledgeBaseId, start, "base_deleted");
        }
        var context = selector.select(result.matches(), limits.maxChunks(), limits.maxContextChars());
        var status = context.sources().isEmpty() ? IncidentGroundingStatus.NO_RELEVANT_CONTEXT
                : IncidentGroundingStatus.CONTEXT_PROVIDED;
        return new GroundingDecision(status, knowledgeBaseId, context, result.totalLatencyMs(),
                result.inputTokens(), result.matches().size());
    }

    private static GroundingDecision failed(UUID knowledgeBaseId, long start, String category) {
        LOGGER.warn("Incident grounding retrieval failed: category={}, knowledgeBaseId={}", category, knowledgeBaseId);
        long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        return new GroundingDecision(IncidentGroundingStatus.RETRIEVAL_FAILED, knowledgeBaseId,
                IncidentAnalysisContext.EMPTY, latency, null, 0);
    }
}
