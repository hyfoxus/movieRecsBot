CREATE TABLE IF NOT EXISTS imdb_person (
    nconst TEXT PRIMARY KEY,
    primary_name TEXT,
    birth_year SMALLINT,
    death_year SMALLINT,
    primary_profession TEXT[],
    known_for_titles TEXT[]
);

CREATE TABLE IF NOT EXISTS movie_aka (
    tconst TEXT NOT NULL,
    ordering INTEGER NOT NULL,
    title TEXT,
    region TEXT,
    language TEXT,
    types TEXT[],
    attributes TEXT[],
    is_original BOOLEAN,
    PRIMARY KEY (tconst, ordering),
    FOREIGN KEY (tconst) REFERENCES movie (tconst) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS movie_principal (
    tconst TEXT NOT NULL,
    ordering INTEGER NOT NULL,
    nconst TEXT NOT NULL,
    category TEXT,
    job TEXT,
    characters TEXT,
    PRIMARY KEY (tconst, ordering),
    FOREIGN KEY (tconst) REFERENCES movie (tconst) ON DELETE CASCADE,
    FOREIGN KEY (nconst) REFERENCES imdb_person (nconst) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS movie_crew (
    tconst TEXT NOT NULL,
    role TEXT NOT NULL,
    nconst TEXT NOT NULL,
    position SMALLINT,
    PRIMARY KEY (tconst, role, nconst, position),
    FOREIGN KEY (tconst) REFERENCES movie (tconst) ON DELETE CASCADE,
    FOREIGN KEY (nconst) REFERENCES imdb_person (nconst) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS movie_episode (
    tconst TEXT PRIMARY KEY,
    parent_tconst TEXT,
    season_number SMALLINT,
    episode_number SMALLINT,
    FOREIGN KEY (tconst) REFERENCES movie (tconst) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_movie_principal_person ON movie_principal (nconst);
CREATE INDEX IF NOT EXISTS idx_movie_crew_person ON movie_crew (nconst);
CREATE INDEX IF NOT EXISTS idx_movie_episode_parent ON movie_episode (parent_tconst);
