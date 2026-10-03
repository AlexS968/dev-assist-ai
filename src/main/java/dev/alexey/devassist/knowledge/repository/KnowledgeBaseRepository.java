package dev.alexey.devassist.knowledge.repository;

import dev.alexey.devassist.knowledge.entity.KnowledgeBase;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBase, UUID> {}
