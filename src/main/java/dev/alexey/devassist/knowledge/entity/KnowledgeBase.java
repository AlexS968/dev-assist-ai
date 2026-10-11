package dev.alexey.devassist.knowledge.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name = "knowledge_bases")
public class KnowledgeBase {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "key", nullable = false, length = 100, unique = true)
    private String key;
    @Column(nullable = false, length = 255)
    private String name;
    @Column(columnDefinition = "text")
    private String description;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KnowledgeBase() {}
    public KnowledgeBase(String key, String name, String description) {
        this.key = key;
        this.name = name;
        this.description = description;
    }
    @PrePersist void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public String getKey() { return key; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
}
