-- Phase 4: Enhanced Metadata Storage Migration
-- This migration adds the new fields required for production-grade metadata tracking

-- Add new columns to the chunks table
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS document_id VARCHAR(255);
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS document_hash VARCHAR(64);
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS chunk_hash VARCHAR(64);
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(100);
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS embedding_dimension INT;
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS language VARCHAR(10);
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS version INT DEFAULT 1;
ALTER TABLE chunks ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- Create indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_document_id ON chunks(document_id);
CREATE INDEX IF NOT EXISTS idx_document_hash ON chunks(document_hash);
CREATE INDEX IF NOT EXISTS idx_chunk_hash ON chunks(chunk_hash);
CREATE INDEX IF NOT EXISTS idx_embedding_model ON chunks(embedding_model);
CREATE INDEX IF NOT EXISTS idx_language ON chunks(language);
CREATE INDEX IF NOT EXISTS idx_version ON chunks(version);
CREATE INDEX IF NOT EXISTS idx_updated_at ON chunks(updated_at);

-- Composite indexes for common queries
CREATE INDEX IF NOT EXISTS idx_doc_version ON chunks(document_id, version);
CREATE INDEX IF NOT EXISTS idx_model_dimension ON chunks(embedding_model, embedding_dimension);

-- Set default values for existing rows (if any)
UPDATE chunks SET document_id = gen_random_uuid()::text WHERE document_id IS NULL;
UPDATE chunks SET document_hash = 'migrated' WHERE document_hash IS NULL;
UPDATE chunks SET chunk_hash = hash WHERE chunk_hash IS NULL AND hash IS NOT NULL;
UPDATE chunks SET embedding_model = 'unknown' WHERE embedding_model IS NULL;
UPDATE chunks SET embedding_dimension = 1536 WHERE embedding_dimension IS NULL;
UPDATE chunks SET language = 'unknown' WHERE language IS NULL;
UPDATE chunks SET version = 1 WHERE version IS NULL;
UPDATE chunks SET updated_at = created_at WHERE updated_at IS NULL;

