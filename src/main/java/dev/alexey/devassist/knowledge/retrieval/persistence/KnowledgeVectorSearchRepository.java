package dev.alexey.devassist.knowledge.retrieval.persistence;

import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
import dev.alexey.devassist.knowledge.retrieval.RetrievedKnowledgeChunk;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.hibernate.type.StandardBasicTypes;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(name = "app.rag.enabled", havingValue = "true")
public class KnowledgeVectorSearchRepository {
    private static final String PROFILE_FILTER = """
            chunk.document.knowledgeBase.id = :baseId
            and chunk.embedding is not null
            and chunk.embeddingProvider = :provider
            and chunk.embeddingModel = :model
            and chunk.embeddingDimensions = :dimensions
            """;
    private static final String DISTANCE = "cosine_distance(chunk.embedding, :queryVector)";
    private final EntityManager entityManager;

    public KnowledgeVectorSearchRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public boolean hasCompatibleEmbeddings(UUID baseId, EmbeddingProfile profile) {
        return !entityManager.createQuery("select chunk.id from KnowledgeChunk chunk where " + PROFILE_FILTER,
                UUID.class)
                .setParameter("baseId", baseId)
                .setParameter("provider", profile.provider())
                .setParameter("model", profile.model())
                .setParameter("dimensions", profile.dimensions())
                .setMaxResults(1).getResultList().isEmpty();
    }

    @Transactional(readOnly = true)
    public List<RetrievedKnowledgeChunk> search(UUID baseId, EmbeddingProfile profile, float[] queryVector,
            int topK, double threshold) {
        String hql = """
                select new dev.alexey.devassist.knowledge.retrieval.RetrievedKnowledgeChunk(
                    chunk.id, chunk.document.id, chunk.document.externalId, chunk.document.title,
                    chunk.document.sourceType, chunk.document.sourceUri, chunk.chunkIndex,
                    chunk.content, chunk.metadata, 1.0 - cosine_distance(chunk.embedding, :queryVector))
                from KnowledgeChunk chunk
                where
                """ + PROFILE_FILTER + " and 1.0 - " + DISTANCE + " >= :threshold"
                + " order by " + DISTANCE + " asc, chunk.id asc";
        var query = entityManager.createQuery(hql, RetrievedKnowledgeChunk.class);
        query.setParameter("baseId", baseId);
        query.setParameter("provider", profile.provider());
        query.setParameter("model", profile.model());
        query.setParameter("dimensions", profile.dimensions());
        query.setParameter("threshold", threshold);
        query.unwrap(org.hibernate.query.Query.class).setParameter("queryVector", queryVector, StandardBasicTypes.VECTOR);
        return query.setMaxResults(topK).getResultList();
    }
}
