CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS movie (
                                     id BIGSERIAL PRIMARY KEY,
                                     tconst TEXT UNIQUE NOT NULL,
                                     primary_title TEXT,
                                     original_title TEXT,
                                     title_type TEXT,
                                     is_adult BOOLEAN,
                                     start_year SMALLINT,
                                     end_year SMALLINT,
                                     runtime_minutes SMALLINT,
                                     genres TEXT[],
                                     rating DOUBLE PRECISION,
                                     votes INTEGER,
                                     plot TEXT,
                                     embedding VECTOR(768),
    embedding_model TEXT,
    embedding_updated_at TIMESTAMPTZ
    );

CREATE TABLE IF NOT EXISTS person (
                                      nconst TEXT PRIMARY KEY,
                                      primary_name TEXT,
                                      birth_year SMALLINT,
                                      death_year SMALLINT,
                                      primary_profession TEXT[]
);

CREATE TABLE IF NOT EXISTS title_akas (
                                          tconst TEXT NOT NULL,
                                          ordering INTEGER,
                                          title TEXT,
                                          region TEXT,
                                          language TEXT,
                                          types TEXT[],
                                          attributes TEXT[],
                                          is_original_title BOOLEAN
);

CREATE TABLE IF NOT EXISTS title_principals (
                                                tconst TEXT NOT NULL,
                                                ordering INTEGER,
                                                nconst TEXT,
                                                category TEXT,
                                                job TEXT,
                                                characters TEXT
);

CREATE TABLE IF NOT EXISTS title_crew (
                                          tconst TEXT PRIMARY KEY,
                                          directors TEXT[],
                                          writers TEXT[]
);

CREATE TABLE IF NOT EXISTS title_episode (
                                             tconst TEXT PRIMARY KEY,
                                             parent_tconst TEXT,
                                             season_number INTEGER,
                                             episode_number INTEGER
);

CREATE TABLE IF NOT EXISTS import_log (
                                          file_name TEXT PRIMARY KEY,
                                          etag TEXT,
                                          last_modified TEXT,
                                          imported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    rows_loaded BIGINT
    );

CREATE INDEX IF NOT EXISTS idx_movie_year ON movie (start_year);
CREATE INDEX IF NOT EXISTS idx_movie_rating_votes ON movie (rating DESC, votes DESC);
CREATE INDEX IF NOT EXISTS idx_movie_genres_gin ON movie USING gin (genres);
CREATE INDEX IF NOT EXISTS idx_movie_embedding_hnsw
    ON movie USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);