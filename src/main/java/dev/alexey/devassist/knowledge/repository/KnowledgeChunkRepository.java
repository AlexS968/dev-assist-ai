package dev.alexey.devassist.knowledge.repository;

import dev.alexey.devassist.knowledge.entity.KnowledgeChunk;
import dev.alexey.devassist.knowledge.embedding.KnowledgeChunkEmbeddingCandidate;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, UUID> {
    long countByDocument_Id(UUID documentId);
    List<KnowledgeChunk> findByDocument_IdOrderByChunkIndexAsc(UUID documentId);
    @Modifying
    @Query("delete from KnowledgeChunk chunk where chunk.document.id = :documentId")
    void deleteByDocumentId(UUID documentId);

    @Query("""
            select new dev.alexey.devassist.knowledge.embedding.KnowledgeChunkEmbeddingCandidate(
                chunk.id, chunk.document.knowledgeBase.id, chunk.content, chunk.contentHash)
            from KnowledgeChunk chunk
            where chunk.document.knowledgeBase.id = :knowledgeBaseId
              and (chunk.embedding is null or chunk.embeddingProvider <> :provider
                   or chunk.embeddingModel <> :model or chunk.embeddingDimensions <> :dimensions)
            order by chunk.document.id, chunk.chunkIndex, chunk.id
            """)
    List<KnowledgeChunkEmbeddingCandidate> findEmbeddingCandidates(UUID knowledgeBaseId,
            String provider, String model, int dimensions, Pageable pageable);
}
