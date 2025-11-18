CREATE TABLE IF NOT EXISTS person (
    id           bigserial PRIMARY KEY,
    nconst       text UNIQUE NOT NULL,
    primary_name text        NOT NULL
);

CREATE TABLE IF NOT EXISTS movie_principal (
    movie_id  bigint NOT NULL REFERENCES movie(id) ON DELETE CASCADE,
    person_id bigint NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    category  text   NOT NULL,
    ordering  integer,
    job       text,
    characters text,
    CONSTRAINT movie_principal_pk PRIMARY KEY (movie_id, person_id, category)
);

CREATE INDEX IF NOT EXISTS idx_movie_principal_person ON movie_principal (person_id);
CREATE INDEX IF NOT EXISTS idx_movie_principal_category ON movie_principal (category);
