package dev.alexey.devassist.knowledge.repository;

import dev.alexey.devassist.knowledge.entity.KnowledgeChunk;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, UUID> {}
