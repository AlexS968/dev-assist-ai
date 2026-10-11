package dev.alexey.devassist.knowledge.repository;

import dev.alexey.devassist.knowledge.entity.KnowledgeDocument;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {
    Optional<KnowledgeDocument> findByKnowledgeBase_IdAndExternalId(UUID baseId, String externalId);
}
