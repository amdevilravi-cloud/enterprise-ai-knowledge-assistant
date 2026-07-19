ALTER TABLE embeddings
ADD COLUMN IF NOT EXISTS search_vector tsvector
GENERATED ALWAYS AS (to_tsvector('english', content)) STORED;

CREATE INDEX IF NOT EXISTS idx_embeddings_fts
ON embeddings USING GIN(search_vector);

CREATE TABLE IF NOT EXISTS search_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    query TEXT,
    vector_score NUMERIC,
    keyword_score NUMERIC,
    fusion_score NUMERIC,
    retrieval_time_ms INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_search_metrics_created ON search_metrics(created_at DESC);

