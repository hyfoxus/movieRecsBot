package com.gnemirko.imdbvec.importer;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

@Component
public class ImdbCopyLoader {

    private final DataSource dataSource;

    public ImdbCopyLoader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long loadTopTitles(ImdbFiles files, int maxTitles) throws Exception {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            return performImport(connection, files, maxTitles);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private long performImport(Connection connection, ImdbFiles files, int maxTitles) throws Exception {
        try (var statement = connection.createStatement()) {
            createTempTables(statement);

            copyFile(connection, files.titleBasics(), """
                COPY tmp_title_basics
                  (tconst, title_type, primary_title, original_title, is_adult,
                   start_year, end_year, runtime_minutes, genres)
                FROM STDIN WITH (FORMAT text)
                """);

            copyFile(connection, files.titleRatings(), """
                COPY tmp_title_ratings (tconst, average_rating, num_votes)
                FROM STDIN WITH (FORMAT text)
                """);

            copyFile(connection, files.titleAkas(), """
                COPY tmp_title_akas
                  (title_id, ordering, title, region, language, types, attributes, is_original_title)
                FROM STDIN WITH (FORMAT text)
                """);

            copyFile(connection, files.titleCrew(), """
                COPY tmp_title_crew (tconst, directors, writers)
                FROM STDIN WITH (FORMAT text)
                """);

            copyFile(connection, files.titlePrincipals(), """
                COPY tmp_title_principals
                  (tconst, ordering, nconst, category, job, characters)
                FROM STDIN WITH (FORMAT text)
                """);

            copyFile(connection, files.titleEpisode(), """
                COPY tmp_title_episode (tconst, parent_tconst, season_number, episode_number)
                FROM STDIN WITH (FORMAT text)
                """);

            copyFile(connection, files.nameBasics(), """
                COPY tmp_name_basics
                  (nconst, primary_name, birth_year, death_year, primary_profession, known_for_titles)
                FROM STDIN WITH (FORMAT text)
                """);

            createAkasAggregation(statement);
            createCrewAggregations(statement);
            createPrincipalsAggregation(statement);
            createEpisodeAggregation(statement);

            populateRankedTitles(statement, maxTitles);
            long affected = upsertMovies(statement);
            deleteMissingMovies(statement);

            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM tmp_ranked_titles")) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            return affected;
        }
    }

    private void createTempTables(java.sql.Statement statement) throws SQLException {
        statement.execute("""
            CREATE TEMP TABLE tmp_title_basics (
              tconst           text,
              title_type       text,
              primary_title    text,
              original_title   text,
              is_adult         text,
              start_year       text,
              end_year         text,
              runtime_minutes  text,
              genres           text
            ) ON COMMIT DROP
            """);

        statement.execute("""
            CREATE TEMP TABLE tmp_title_ratings (
              tconst         text,
              average_rating text,
              num_votes      text
            ) ON COMMIT DROP
            """);

        statement.execute("""
            CREATE TEMP TABLE tmp_title_akas (
              title_id         text,
              ordering         text,
              title            text,
              region           text,
              language         text,
              types            text,
              attributes       text,
              is_original_title text
            ) ON COMMIT DROP
            """);

        statement.execute("""
            CREATE TEMP TABLE tmp_title_crew (
              tconst    text,
              directors text,
              writers   text
            ) ON COMMIT DROP
            """);

        statement.execute("""
            CREATE TEMP TABLE tmp_title_principals (
              tconst     text,
              ordering   text,
              nconst     text,
              category   text,
              job        text,
              characters text
            ) ON COMMIT DROP
            """);

        statement.execute("""
            CREATE TEMP TABLE tmp_title_episode (
              tconst        text,
              parent_tconst text,
              season_number text,
              episode_number text
            ) ON COMMIT DROP
            """);

        statement.execute("""
            CREATE TEMP TABLE tmp_name_basics (
              nconst             text,
              primary_name       text,
              birth_year         text,
              death_year         text,
              primary_profession text,
              known_for_titles   text
            ) ON COMMIT DROP
            """);
    }

    private void createAkasAggregation(java.sql.Statement statement) throws SQLException {
        statement.execute("""
            CREATE TEMP TABLE tmp_akas_json AS
            SELECT
              title_id AS tconst,
              jsonb_agg(
                jsonb_strip_nulls(
                  jsonb_build_object(
                    'ordering', NULLIF(ordering, '\\N')::integer,
                    'title', NULLIF(title, '\\N'),
                    'region', NULLIF(region, '\\N'),
                    'language', NULLIF(language, '\\N'),
                    'types', CASE
                      WHEN types IS NULL OR types = '\\N' THEN NULL
                      ELSE to_jsonb(string_to_array(types, ','))
                    END,
                    'attributes', CASE
                      WHEN attributes IS NULL OR attributes = '\\N' THEN NULL
                      ELSE to_jsonb(string_to_array(attributes, ','))
                    END,
                    'isOriginalTitle', CASE
                      WHEN is_original_title IS NULL OR is_original_title = '\\N' THEN NULL
                      WHEN is_original_title IN ('1','t','true','TRUE') THEN TRUE
                      WHEN is_original_title IN ('0','f','false','FALSE') THEN FALSE
                      ELSE NULL
                    END
                  )
                )
                ORDER BY NULLIF(ordering, '\\N')::integer
              ) AS akas
            FROM tmp_title_akas
            GROUP BY title_id
            """);
    }

    private void createCrewAggregations(java.sql.Statement statement) throws SQLException {
        statement.execute("""
            CREATE TEMP TABLE tmp_directors_json AS
            SELECT
              tc.tconst,
              jsonb_agg(
                jsonb_strip_nulls(
                  jsonb_build_object(
                    'ordering', dir.ord,
                    'nconst', COALESCE(nb.nconst, dir.nconst),
                    'name', nb.primary_name,
                    'birthYear', NULLIF(nb.birth_year, '\\N')::integer,
                    'deathYear', NULLIF(nb.death_year, '\\N')::integer,
                    'primaryProfession', CASE
                      WHEN nb.primary_profession IS NULL OR nb.primary_profession = '\\N' THEN NULL
                      ELSE to_jsonb(string_to_array(nb.primary_profession, ','))
                    END,
                    'knownForTitles', CASE
                      WHEN nb.known_for_titles IS NULL OR nb.known_for_titles = '\\N' THEN NULL
                      ELSE to_jsonb(string_to_array(nb.known_for_titles, ','))
                    END
                  )
                )
                ORDER BY dir.ord
              ) AS directors
            FROM tmp_title_crew tc
            JOIN LATERAL (
              SELECT
                NULLIF(trim(dir.value), '') AS nconst,
                dir.ord
              FROM unnest(
                     CASE
                       WHEN tc.directors IS NULL OR tc.directors = '\\N' THEN ARRAY[]::text[]
                       ELSE string_to_array(tc.directors, ',')
                     END
                   ) WITH ORDINALITY AS dir(value, ord)
              WHERE NULLIF(trim(dir.value), '') IS NOT NULL
            ) dir ON TRUE
            LEFT JOIN tmp_name_basics nb ON nb.nconst = dir.nconst
            GROUP BY tc.tconst
            """);

        statement.execute("""
            CREATE TEMP TABLE tmp_writers_json AS
            SELECT
              tc.tconst,
              jsonb_agg(
                jsonb_strip_nulls(
                  jsonb_build_object(
                    'ordering', wr.ord,
                    'nconst', COALESCE(nb.nconst, wr.nconst),
                    'name', nb.primary_name,
                    'birthYear', NULLIF(nb.birth_year, '\\N')::integer,
                    'deathYear', NULLIF(nb.death_year, '\\N')::integer,
                    'primaryProfession', CASE
                      WHEN nb.primary_profession IS NULL OR nb.primary_profession = '\\N' THEN NULL
                      ELSE to_jsonb(string_to_array(nb.primary_profession, ','))
                    END,
                    'knownForTitles', CASE
                      WHEN nb.known_for_titles IS NULL OR nb.known_for_titles = '\\N' THEN NULL
                      ELSE to_jsonb(string_to_array(nb.known_for_titles, ','))
                    END
                  )
                )
                ORDER BY wr.ord
              ) AS writers
            FROM tmp_title_crew tc
            JOIN LATERAL (
              SELECT
                NULLIF(trim(dir.value), '') AS nconst,
                dir.ord
              FROM unnest(
                     CASE
                       WHEN tc.writers IS NULL OR tc.writers = '\\N' THEN ARRAY[]::text[]
                       ELSE string_to_array(tc.writers, ',')
                     END
                   ) WITH ORDINALITY AS dir(value, ord)
              WHERE NULLIF(trim(dir.value), '') IS NOT NULL
            ) wr ON TRUE
            LEFT JOIN tmp_name_basics nb ON nb.nconst = wr.nconst
            GROUP BY tc.tconst
            """);
    }

    private void createPrincipalsAggregation(java.sql.Statement statement) throws SQLException {
        statement.execute("""
            CREATE TEMP TABLE tmp_principals_json AS
            SELECT
              p.tconst,
              jsonb_agg(
                jsonb_strip_nulls(
                  jsonb_build_object(
                    'ordering', NULLIF(p.ordering, '\\N')::integer,
                    'category', NULLIF(p.category, '\\N'),
                    'job', NULLIF(p.job, '\\N'),
                    'characters', CASE
                      WHEN p.characters IS NULL OR p.characters = '\\N' THEN NULL
                      ELSE p.characters::jsonb
                    END,
                    'person', CASE
                      WHEN nb.nconst IS NULL AND (p.nconst IS NULL OR p.nconst = '\\N') THEN NULL
                      ELSE NULLIF(
                        jsonb_strip_nulls(
                          jsonb_build_object(
                            'nconst', COALESCE(nb.nconst, NULLIF(p.nconst, '\\N')),
                            'name', nb.primary_name,
                            'birthYear', NULLIF(nb.birth_year, '\\N')::integer,
                            'deathYear', NULLIF(nb.death_year, '\\N')::integer,
                            'primaryProfession', CASE
                              WHEN nb.primary_profession IS NULL OR nb.primary_profession = '\\N' THEN NULL
                              ELSE to_jsonb(string_to_array(nb.primary_profession, ','))
                            END,
                            'knownForTitles', CASE
                              WHEN nb.known_for_titles IS NULL OR nb.known_for_titles = '\\N' THEN NULL
                              ELSE to_jsonb(string_to_array(nb.known_for_titles, ','))
                            END
                          )
                        ),
                        '{}'::jsonb
                      )
                    END
                  )
                )
                ORDER BY NULLIF(p.ordering, '\\N')::integer
              ) AS principals
            FROM tmp_title_principals p
            LEFT JOIN tmp_name_basics nb ON nb.nconst = NULLIF(p.nconst, '\\N')
            GROUP BY p.tconst
            """);
    }

    private void createEpisodeAggregation(java.sql.Statement statement) throws SQLException {
        statement.execute("""
            CREATE TEMP TABLE tmp_episode_json AS
            SELECT
              tconst,
              jsonb_strip_nulls(
                jsonb_build_object(
                  'parentTconst', NULLIF(parent_tconst, '\\N'),
                  'seasonNumber', NULLIF(season_number, '\\N')::integer,
                  'episodeNumber', NULLIF(episode_number, '\\N')::integer
                )
              ) AS episode
            FROM tmp_title_episode
            WHERE
              (parent_tconst IS NOT NULL AND parent_tconst <> '\\N')
              OR (season_number IS NOT NULL AND season_number <> '\\N')
              OR (episode_number IS NOT NULL AND episode_number <> '\\N')
            """);
    }

    private void copyFile(Connection connection, Path source, String copySql) throws Exception {
        CopyManager copyManager = connection.unwrap(PGConnection.class).getCopyAPI();
        try (InputStream fis = Files.newInputStream(source);
             InputStream bis = new BufferedInputStream(fis);
             GZIPInputStream gz = new GZIPInputStream(bis)) {
            skipHeaderLine(gz);
            copyManager.copyIn(copySql, gz);
        }
    }

    private void populateRankedTitles(java.sql.Statement statement, int maxTitles) throws SQLException {
        String limitClause = maxTitles > 0 ? "LIMIT " + maxTitles : "";
        statement.execute("""
            CREATE TEMP TABLE tmp_ranked_titles AS
            SELECT
              b.tconst,
              NULLIF(b.title_type, '\\N') AS title_type,
              NULLIF(b.primary_title, '\\N') AS primary_title,
              NULLIF(b.original_title, '\\N') AS original_title,
              CASE
                WHEN b.is_adult IS NULL OR b.is_adult = '\\N' THEN NULL
                WHEN b.is_adult IN ('1','t','true','TRUE') THEN TRUE
                WHEN b.is_adult IN ('0','f','false','FALSE') THEN FALSE
                ELSE NULL
              END AS is_adult,
              NULLIF(b.start_year, '\\N')::smallint AS start_year,
              NULLIF(b.end_year, '\\N')::smallint AS end_year,
              CASE
                WHEN b.runtime_minutes IS NULL OR b.runtime_minutes = '\\N' THEN NULL
                WHEN b.runtime_minutes !~ '^-?\\d+$' THEN NULL
                WHEN (b.runtime_minutes)::integer BETWEEN -32768 AND 32767
                  THEN b.runtime_minutes::smallint
                ELSE NULL
              END AS runtime_minutes,
              CASE
                WHEN b.genres IS NULL OR b.genres = '\\N' THEN NULL
                ELSE string_to_array(b.genres, ',')::text[]
              END AS genres,
              NULLIF(r.average_rating, '\\N')::double precision AS rating,
              NULLIF(r.num_votes, '\\N')::integer AS votes,
              ak.akas,
              dir.directors,
              wr.writers,
              pr.principals,
              ep.episode
            FROM tmp_title_basics b
            LEFT JOIN tmp_title_ratings r ON r.tconst = b.tconst
            LEFT JOIN tmp_akas_json ak ON ak.tconst = b.tconst
            LEFT JOIN tmp_directors_json dir ON dir.tconst = b.tconst
            LEFT JOIN tmp_writers_json wr ON wr.tconst = b.tconst
            LEFT JOIN tmp_principals_json pr ON pr.tconst = b.tconst
            LEFT JOIN tmp_episode_json ep ON ep.tconst = b.tconst
            ORDER BY
              COALESCE(NULLIF(r.average_rating, '\\N')::double precision, -1) DESC,
              COALESCE(NULLIF(r.num_votes, '\\N')::bigint, 0) DESC,
              b.tconst
            %s
            """.formatted(limitClause));
    }

    private long upsertMovies(java.sql.Statement statement) throws SQLException {
        return statement.executeUpdate("""
            INSERT INTO movie
              (tconst, title_type, primary_title, original_title, is_adult,
               start_year, end_year, runtime_minutes, genres, rating, votes,
               akas, directors, writers, principals, episode)
            SELECT
              tconst,
              title_type,
              primary_title,
              original_title,
              is_adult,
              start_year,
              end_year,
              runtime_minutes,
              genres,
              rating,
              votes,
              akas,
              directors,
              writers,
              principals,
              episode
            FROM tmp_ranked_titles
            ON CONFLICT (tconst) DO UPDATE
              SET title_type      = EXCLUDED.title_type,
                  primary_title   = EXCLUDED.primary_title,
                  original_title  = EXCLUDED.original_title,
                  is_adult        = EXCLUDED.is_adult,
                  start_year      = EXCLUDED.start_year,
                  end_year        = EXCLUDED.end_year,
                  runtime_minutes = EXCLUDED.runtime_minutes,
                  genres          = EXCLUDED.genres,
                  rating          = EXCLUDED.rating,
                  votes           = EXCLUDED.votes,
                  akas            = EXCLUDED.akas,
                  directors       = EXCLUDED.directors,
                  writers         = EXCLUDED.writers,
                  principals      = EXCLUDED.principals,
                  episode         = EXCLUDED.episode
            """);
    }

    private void deleteMissingMovies(java.sql.Statement statement) throws SQLException {
        statement.execute("""
            DELETE FROM movie m
            WHERE NOT EXISTS (
              SELECT 1 FROM tmp_ranked_titles t WHERE t.tconst = m.tconst
            )
            """);
    }

    private void skipHeaderLine(InputStream in) throws Exception {
        int b;
        boolean sawCR = false;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b == '\r') { sawCR = true; break; }
        }
        if (sawCR) {
            in.mark(1);
            int next = in.read();
            if (next != '\n') {
                in.reset();
            }
        }
    }

    public static ImdbFiles.Builder builder() {
        return ImdbFiles.builder();
    }

    public record ImdbFiles(
            Path titleBasics,
            Path titleRatings,
            Path titleAkas,
            Path titleCrew,
            Path titlePrincipals,
            Path titleEpisode,
            Path nameBasics
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private Path titleBasics;
            private Path titleRatings;
            private Path titleAkas;
            private Path titleCrew;
            private Path titlePrincipals;
            private Path titleEpisode;
            private Path nameBasics;
            private Builder() {}

            public Builder titleBasics(Path path) {
                this.titleBasics = path;
                return this;
            }

            public Builder titleRatings(Path path) {
                this.titleRatings = path;
                return this;
            }

            public Builder titleAkas(Path path) {
                this.titleAkas = path;
                return this;
            }

            public Builder titleCrew(Path path) {
                this.titleCrew = path;
                return this;
            }

            public Builder titlePrincipals(Path path) {
                this.titlePrincipals = path;
                return this;
            }

            public Builder titleEpisode(Path path) {
                this.titleEpisode = path;
                return this;
            }

            public Builder nameBasics(Path path) {
                this.nameBasics = path;
                return this;
            }

            public ImdbFiles build() {
                return new ImdbFiles(
                        Objects.requireNonNull(titleBasics, "titleBasics file is required"),
                        Objects.requireNonNull(titleRatings, "titleRatings file is required"),
                        Objects.requireNonNull(titleAkas, "titleAkas file is required"),
                        Objects.requireNonNull(titleCrew, "titleCrew file is required"),
                        Objects.requireNonNull(titlePrincipals, "titlePrincipals file is required"),
                        Objects.requireNonNull(titleEpisode, "titleEpisode file is required"),
                        Objects.requireNonNull(nameBasics, "nameBasics file is required")
                );
            }
        }
    }
}
