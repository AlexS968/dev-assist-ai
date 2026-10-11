CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_bases
(
    id UUID PRIMARY KEY,
    key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE knowledge_documents
(
    id UUID PRIMARY KEY,
    knowledge_base_id UUID NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    external_id VARCHAR(255) NOT NULL,
    title VARCHAR(500) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_uri VARCHAR(2000),
    content_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_knowledge_documents_base_external UNIQUE (knowledge_base_id, external_id)
);
CREATE INDEX idx_knowledge_documents_base ON knowledge_documents(knowledge_base_id);

CREATE TABLE knowledge_chunks
(
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES knowledge_documents(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_knowledge_chunks_document_index UNIQUE (document_id, chunk_index)
);
CREATE INDEX idx_knowledge_chunks_document ON knowledge_chunks(document_id);
