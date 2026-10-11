ALTER TABLE knowledge_chunks
    ADD COLUMN embedding vector(768),
    ADD COLUMN embedding_provider VARCHAR(50),
    ADD COLUMN embedding_model VARCHAR(255),
    ADD COLUMN embedding_dimensions INTEGER,
    ADD COLUMN embedded_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE knowledge_chunks
    ADD CONSTRAINT chk_knowledge_chunks_embedding_state CHECK (
        (embedding IS NULL AND embedding_provider IS NULL AND embedding_model IS NULL
            AND embedding_dimensions IS NULL AND embedded_at IS NULL)
        OR
        (embedding IS NOT NULL AND embedding_provider IS NOT NULL AND embedding_model IS NOT NULL
            AND embedding_dimensions IS NOT NULL AND embedded_at IS NOT NULL)
    ),
    ADD CONSTRAINT chk_knowledge_chunks_embedding_dimensions CHECK (
        embedding_dimensions IS NULL OR embedding_dimensions = 768
    );
