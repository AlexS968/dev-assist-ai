package dev.alexey.devassist.knowledge.repository;

import dev.alexey.devassist.knowledge.entity.KnowledgeChunk;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, UUID> {
    long countByDocument_Id(UUID documentId);
    List<KnowledgeChunk> findByDocument_IdOrderByChunkIndexAsc(UUID documentId);
    @Modifying
    @Query("delete from KnowledgeChunk chunk where chunk.document.id = :documentId")
    void deleteByDocumentId(UUID documentId);
}
