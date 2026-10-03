package dev.alexey.devassist.knowledge.entity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "knowledge_chunks", uniqueConstraints = @UniqueConstraint(name = "uk_knowledge_chunks_document_index", columnNames = {"document_id", "chunk_index"}), indexes = @Index(name = "idx_knowledge_chunks_document", columnList = "document_id"))
public class KnowledgeChunk {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private KnowledgeDocument document;
    @Column(name = "chunk_index", nullable = false, updatable = false)
    private int chunkIndex;
    @Column(nullable = false, columnDefinition = "text")
    private String content;
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> metadata;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected KnowledgeChunk() {}
    public KnowledgeChunk(KnowledgeDocument document, int chunkIndex, String content,
            String contentHash, Map<String, String> metadata) {
        this.document = document;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.contentHash = contentHash;
        this.metadata = Map.copyOf(metadata);
    }
    @PrePersist void onCreate() { createdAt = Instant.now(); }
    public UUID getId() { return id; }
    public KnowledgeDocument getDocument() { return document; }
    public int getChunkIndex() { return chunkIndex; }
    public String getContent() { return content; }
    public String getContentHash() { return contentHash; }
    public Map<String, String> getMetadata() { return Map.copyOf(metadata); }
    public Instant getCreatedAt() { return createdAt; }
}
