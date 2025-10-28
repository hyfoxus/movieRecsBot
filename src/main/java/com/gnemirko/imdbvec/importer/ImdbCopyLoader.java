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

            copyFile(connection, files.titlePrincipals(), """
                COPY tmp_title_principals
                  (tconst, ordering, nconst, category, job, characters)
                FROM STDIN WITH (FORMAT text)
                """);

            copyFile(connection, files.titleCrew(), """
                COPY tmp_title_crew (tconst, directors, writers)
                FROM STDIN WITH (FORMAT text)
                """);

            copyFile(connection, files.titleEpisode(), """
                COPY tmp_title_episode
                  (tconst, parent_tconst, season_number, episode_number)
                FROM STDIN WITH (FORMAT text)
                """);

            copyFile(connection, files.nameBasics(), """
                COPY tmp_name_basics
                  (nconst, primary_name, birth_year, death_year, primary_profession, known_for_titles)
                FROM STDIN WITH (FORMAT text)
                """);

            populateRankedTitles(statement, maxTitles);
            long affected = upsertMovies(statement);
            deleteMissingMovies(statement);

            truncateDetailTables(statement);
            populatePeople(statement);
            populateAkas(statement);
            populatePrincipals(statement);
            populateCrew(statement);
            populateEpisodes(statement);

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
              title_id            text,
              ordering            text,
              title               text,
              region              text,
              language            text,
              types               text,
              attributes          text,
              is_original_title   text
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
            CREATE TEMP TABLE tmp_title_crew (
              tconst    text,
              directors text,
              writers   text
            ) ON COMMIT DROP
            """);

        statement.execute("""
            CREATE TEMP TABLE tmp_title_episode (
              tconst         text,
              parent_tconst  text,
              season_number  text,
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
              NULLIF(r.num_votes, '\\N')::integer AS votes
            FROM tmp_title_basics b
            LEFT JOIN tmp_title_ratings r ON r.tconst = b.tconst
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
               start_year, end_year, runtime_minutes, genres, rating, votes)
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
              votes
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
                  votes           = EXCLUDED.votes
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

    private void truncateDetailTables(java.sql.Statement statement) throws SQLException {
        statement.execute("TRUNCATE TABLE movie_principal, movie_crew, movie_aka, movie_episode, imdb_person");
    }

    private void populatePeople(java.sql.Statement statement) throws SQLException {
        statement.execute("""
            CREATE TEMP TABLE tmp_people AS
            SELECT DISTINCT nconst
            FROM (
                  SELECT NULLIF(p.nconst, '\\N') AS nconst
                  FROM tmp_title_principals p
                  JOIN tmp_ranked_titles t ON t.tconst = p.tconst
                  WHERE NULLIF(p.nconst, '\\N') IS NOT NULL

                  UNION

                  SELECT TRIM(val) AS nconst
                  FROM tmp_title_crew c
                  JOIN tmp_ranked_titles t ON t.tconst = c.tconst
                  CROSS JOIN LATERAL unnest(string_to_array(NULLIF(c.directors, '\\N'), ',')) WITH ORDINALITY AS vals(val, ord)
                  WHERE NULLIF(c.directors, '\\N') IS NOT NULL

                  UNION

                  SELECT TRIM(val) AS nconst
                  FROM tmp_title_crew c
                  JOIN tmp_ranked_titles t ON t.tconst = c.tconst
                  CROSS JOIN LATERAL unnest(string_to_array(NULLIF(c.writers, '\\N'), ',')) WITH ORDINALITY AS vals(val, ord)
                  WHERE NULLIF(c.writers, '\\N') IS NOT NULL
            ) AS combined
            WHERE nconst IS NOT NULL AND nconst <> ''
            """);

        statement.executeUpdate("""
            INSERT INTO imdb_person (nconst, primary_name, birth_year, death_year, primary_profession, known_for_titles)
            SELECT
              nb.nconst,
              NULLIF(nb.primary_name, '\\N') AS primary_name,
              NULLIF(nb.birth_year, '\\N')::smallint AS birth_year,
              NULLIF(nb.death_year, '\\N')::smallint AS death_year,
              CASE
                WHEN nb.primary_profession IS NULL OR nb.primary_profession = '\\N' THEN NULL
                ELSE string_to_array(nb.primary_profession, ',')::text[]
              END AS primary_profession,
              CASE
                WHEN nb.known_for_titles IS NULL OR nb.known_for_titles = '\\N' THEN NULL
                ELSE string_to_array(nb.known_for_titles, ',')::text[]
              END AS known_for_titles
            FROM tmp_name_basics nb
            JOIN tmp_people p ON p.nconst = nb.nconst
            """);
    }

    private void populateAkas(java.sql.Statement statement) throws SQLException {
        statement.executeUpdate("""
            INSERT INTO movie_aka (tconst, ordering, title, region, language, types, attributes, is_original)
            SELECT
              a.title_id,
              NULLIF(a.ordering, '\\N')::integer AS ordering,
              NULLIF(a.title, '\\N') AS title,
              NULLIF(a.region, '\\N') AS region,
              NULLIF(a.language, '\\N') AS language,
              CASE
                WHEN a.types IS NULL OR a.types = '\\N' THEN NULL
                ELSE string_to_array(a.types, ',')::text[]
              END AS types,
              CASE
                WHEN a.attributes IS NULL OR a.attributes = '\\N' THEN NULL
                ELSE string_to_array(a.attributes, ',')::text[]
              END AS attributes,
              CASE
                WHEN a.is_original_title IN ('1','t','true','TRUE') THEN TRUE
                WHEN a.is_original_title IN ('0','f','false','FALSE') THEN FALSE
                ELSE NULL
              END AS is_original
            FROM tmp_title_akas a
            JOIN tmp_ranked_titles t ON t.tconst = a.title_id
            WHERE NULLIF(a.ordering, '\\N') IS NOT NULL
            """);
    }

    private void populatePrincipals(java.sql.Statement statement) throws SQLException {
        statement.executeUpdate("""
            INSERT INTO movie_principal (tconst, ordering, nconst, category, job, characters)
            SELECT
              p.tconst,
              NULLIF(p.ordering, '\\N')::integer AS ordering,
              NULLIF(p.nconst, '\\N') AS nconst,
              NULLIF(p.category, '\\N') AS category,
              NULLIF(p.job, '\\N') AS job,
              CASE
                WHEN p.characters IS NULL OR p.characters = '\\N' THEN NULL
                ELSE p.characters
              END AS characters
            FROM tmp_title_principals p
            JOIN tmp_ranked_titles t ON t.tconst = p.tconst
            WHERE NULLIF(p.ordering, '\\N') IS NOT NULL
              AND NULLIF(p.nconst, '\\N') IS NOT NULL
            """);
    }

    private void populateCrew(java.sql.Statement statement) throws SQLException {
        statement.executeUpdate("""
            INSERT INTO movie_crew (tconst, role, nconst, position)
            SELECT tconst, role, nconst, position
            FROM (
              SELECT
                c.tconst,
                'director' AS role,
                TRIM(val) AS nconst,
                ord::smallint AS position
              FROM tmp_title_crew c
              JOIN tmp_ranked_titles t ON t.tconst = c.tconst
              CROSS JOIN LATERAL unnest(string_to_array(NULLIF(c.directors, '\\N'), ',')) WITH ORDINALITY AS vals(val, ord)
              WHERE NULLIF(c.directors, '\\N') IS NOT NULL

              UNION ALL

              SELECT
                c.tconst,
                'writer' AS role,
                TRIM(val) AS nconst,
                ord::smallint AS position
              FROM tmp_title_crew c
              JOIN tmp_ranked_titles t ON t.tconst = c.tconst
              CROSS JOIN LATERAL unnest(string_to_array(NULLIF(c.writers, '\\N'), ',')) WITH ORDINALITY AS vals(val, ord)
              WHERE NULLIF(c.writers, '\\N') IS NOT NULL
            ) crew
            WHERE nconst IS NOT NULL AND nconst <> ''
            """);
    }

    private void populateEpisodes(java.sql.Statement statement) throws SQLException {
        statement.executeUpdate("""
            INSERT INTO movie_episode (tconst, parent_tconst, season_number, episode_number)
            SELECT
              e.tconst,
              NULLIF(e.parent_tconst, '\\N') AS parent_tconst,
              NULLIF(e.season_number, '\\N')::smallint AS season_number,
              NULLIF(e.episode_number, '\\N')::smallint AS episode_number
            FROM tmp_title_episode e
            JOIN tmp_ranked_titles t ON t.tconst = e.tconst
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
            Path titlePrincipals,
            Path titleCrew,
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
            private Path titlePrincipals;
            private Path titleCrew;
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

            public Builder titlePrincipals(Path path) {
                this.titlePrincipals = path;
                return this;
            }

            public Builder titleCrew(Path path) {
                this.titleCrew = path;
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
                        Objects.requireNonNull(titlePrincipals, "titlePrincipals file is required"),
                        Objects.requireNonNull(titleCrew, "titleCrew file is required"),
                        Objects.requireNonNull(titleEpisode, "titleEpisode file is required"),
                        Objects.requireNonNull(nameBasics, "nameBasics file is required")
                );
            }
        }
    }
}
