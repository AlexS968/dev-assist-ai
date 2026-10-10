ALTER TABLE incidents
    ADD COLUMN knowledge_base_id UUID NULL,
    ADD CONSTRAINT fk_incidents_knowledge_base
        FOREIGN KEY (knowledge_base_id) REFERENCES knowledge_bases(id) ON DELETE SET NULL;

CREATE INDEX idx_incidents_knowledge_base ON incidents(knowledge_base_id);
