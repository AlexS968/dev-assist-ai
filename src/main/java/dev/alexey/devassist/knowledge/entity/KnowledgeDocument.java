package dev.alexey.devassist.knowledge.entity;

import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "knowledge_documents", uniqueConstraints = @UniqueConstraint(name = "uk_knowledge_documents_base_external", columnNames = {"knowledge_base_id", "external_id"}), indexes = @Index(name = "idx_knowledge_documents_base", columnList = "knowledge_base_id"))
public class KnowledgeDocument {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_base_id", nullable = false, updatable = false)
    private KnowledgeBase knowledgeBase;
    @Column(name = "external_id", nullable = false, length = 255, updatable = false)
    private String externalId;
    @Column(nullable = false, length = 500)
    private String title;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private KnowledgeSourceType sourceType;
    @Column(name = "source_uri", length = 2000)
    private String sourceUri;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KnowledgeDocument() {}
    public KnowledgeDocument(KnowledgeBase knowledgeBase, String externalId, String title,
            KnowledgeSourceType sourceType, String sourceUri, String contentHash) {
        this.knowledgeBase = knowledgeBase;
        this.externalId = externalId;
        this.title = title;
        this.sourceType = sourceType;
        this.sourceUri = sourceUri;
        this.contentHash = contentHash;
    }
    @PrePersist void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public KnowledgeBase getKnowledgeBase() { return knowledgeBase; }
    public String getExternalId() { return externalId; }
    public String getTitle() { return title; }
    public KnowledgeSourceType getSourceType() { return sourceType; }
    public String getSourceUri() { return sourceUri; }
    public String getContentHash() { return contentHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setTitle(String title) { this.title = title; }
    public void setSourceType(KnowledgeSourceType sourceType) { this.sourceType = sourceType; }
    public void setSourceUri(String sourceUri) { this.sourceUri = sourceUri; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
}
