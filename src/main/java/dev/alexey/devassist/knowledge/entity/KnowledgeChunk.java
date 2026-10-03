package dev.alexey.devassist.knowledge.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import dev.alexey.devassist.knowledge.enums.KnowledgeSourceType;
import dev.alexey.devassist.knowledge.embedding.EmbeddingProfile;
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

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(columnDefinition = "vector(768)")
    private float[] embedding;
    @Column(name = "embedding_provider", length = 50)
    private String embeddingProvider;
    @Column(name = "embedding_model", length = 255)
    private String embeddingModel;
    @Column(name = "embedding_dimensions")
    private Integer embeddingDimensions;
    @Column(name = "embedded_at")
    private Instant embeddedAt;
    @Version
    @Column(nullable = false)
    private long version;

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
    public float[] getEmbedding() { return embedding == null ? null : embedding.clone(); }
    public String getEmbeddingProvider() { return embeddingProvider; }
    public String getEmbeddingModel() { return embeddingModel; }
    public Integer getEmbeddingDimensions() { return embeddingDimensions; }
    public Instant getEmbeddedAt() { return embeddedAt; }
    public long getVersion() { return version; }

    public void setEmbedding(float[] vector, EmbeddingProfile profile, Instant embeddedAt) {
        if (vector == null || profile == null || embeddedAt == null || vector.length != 768) {
            throw new IllegalArgumentException("Invalid embedding state");
        }
        for (float value : vector) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Embedding must be finite");
        }
        if (profile.dimensions() != 768) throw new IllegalArgumentException("Embedding dimensions must be 768");
        embedding = vector.clone();
        embeddingProvider = profile.provider();
        embeddingModel = profile.model();
        embeddingDimensions = profile.dimensions();
        this.embeddedAt = embeddedAt;
    }

    public void clearEmbedding() {
        embedding = null;
        embeddingProvider = null;
        embeddingModel = null;
        embeddingDimensions = null;
        embeddedAt = null;
    }

    public void updateSourceTypeMetadata(KnowledgeSourceType sourceType) {
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType is required");
        }
        Map<String, String> updated = new HashMap<>(metadata);
        updated.put("sourceType", sourceType.name());
        metadata = Map.copyOf(updated);
    }
}
