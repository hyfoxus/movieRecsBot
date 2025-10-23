CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS movie (
                                     id               bigserial PRIMARY KEY,
                                     tconst           text UNIQUE NOT NULL,
                                     title_type       text,
                                     primary_title    text,
                                     original_title   text,
                                     is_adult         boolean,
                                     start_year       smallint,
                                     end_year         smallint,
                                     runtime_minutes  smallint,
                                     genres           text[],
                                     rating           double precision,
                                     votes            integer,
                                     plot             text,
                                     embedding        vector(768),
                                     embedding_model  text,
                                     embedding_updated_at timestamptz
    );

CREATE INDEX IF NOT EXISTS idx_movie_year ON movie (start_year);
CREATE INDEX IF NOT EXISTS idx_movie_adult ON movie (is_adult);
CREATE INDEX IF NOT EXISTS idx_movie_genres_gin ON movie USING gin (genres);


DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_movie_embedding_hnsw'
    ) THEN
CREATE INDEX idx_movie_embedding_hnsw ON movie
    USING hnsw (embedding vector_cosine_ops);
END IF;
END $$;
