package dev.alexey.devassist.knowledge.repository;

import dev.alexey.devassist.knowledge.entity.KnowledgeDocument;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {}
