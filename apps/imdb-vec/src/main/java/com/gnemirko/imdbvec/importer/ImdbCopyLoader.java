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

    private static final String RANKING_ORDER = """
              COALESCE(NULLIF(r.average_rating, '\\N')::double precision, -1) DESC,
              COALESCE(NULLIF(r.num_votes, '\\N')::bigint, 0) DESC,
              b.tconst
            """;
    private static final String TITLE_TYPE_WHERE = "LOWER(NULLIF(b.title_type, '\\\\N')) IN ('movie','tvmovie')";

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

            copyFile(connection, files.nameBasics(), """
                COPY tmp_name_basics
                  (nconst, primary_name, birth_year, death_year, primary_profession, known_for_titles)
                FROM STDIN WITH (FORMAT text)
                """);

            copyFile(connection, files.titlePrincipals(), """
                COPY tmp_title_principals
                  (tconst, ordering, nconst, category, job, characters)
                FROM STDIN WITH (FORMAT text)
                """);

            createSelectedTitles(statement, maxTitles);

            populateRankedTitles(statement, maxTitles);
            long affected = upsertMovies(statement);
            deleteMissingMovies(statement);
            syncPrincipals(statement);

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
            CREATE TEMP TABLE tmp_name_basics (
              nconst             text,
              primary_name       text,
              birth_year         text,
              death_year         text,
              primary_profession text,
              known_for_titles   text
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

    }

    private void createSelectedTitles(java.sql.Statement statement, int maxTitles) throws SQLException {
        String selectionFilter = maxTitles > 0 ? "WHERE rn <= " + maxTitles : "";
        statement.execute("""
            CREATE TEMP TABLE tmp_selected_titles ON COMMIT DROP AS
            SELECT tconst
            FROM (
              SELECT
                b.tconst,
                ROW_NUMBER() OVER (
                  ORDER BY
%s
                ) AS rn
              FROM tmp_title_basics b
              LEFT JOIN tmp_title_ratings r ON r.tconst = b.tconst
              WHERE %s
            ) ranked
            %s
            """.formatted(RANKING_ORDER, TITLE_TYPE_WHERE, selectionFilter));
        statement.execute("CREATE INDEX tmp_selected_titles_tconst_idx ON tmp_selected_titles (tconst)");
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
            JOIN tmp_selected_titles st ON st.tconst = b.tconst
            LEFT JOIN tmp_title_ratings r ON r.tconst = b.tconst
            ORDER BY
%s
            %s
            """.formatted(RANKING_ORDER, limitClause));
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

    private void syncPrincipals(java.sql.Statement statement) throws SQLException {
        statement.execute("""
            CREATE TEMP TABLE tmp_filtered_principals ON COMMIT DROP AS
            SELECT DISTINCT ON (p.tconst, clean.nconst, clean.category)
              p.tconst,
              clean.nconst,
              clean.category,
              clean.ordering,
              clean.job,
              clean.characters
            FROM tmp_title_principals p
            JOIN tmp_selected_titles st ON st.tconst = p.tconst
            CROSS JOIN LATERAL (
              SELECT
                NULLIF(p.nconst, '\\N') AS nconst,
                LOWER(NULLIF(p.category, '\\N')) AS category,
                CASE
                  WHEN p.ordering IS NULL OR p.ordering = '\\N' THEN NULL
                  WHEN p.ordering ~ '^\\d+$' THEN p.ordering::integer
                  ELSE NULL
                END AS ordering,
                NULLIF(p.job, '\\N') AS job,
                NULLIF(p.characters, '\\N') AS characters
            ) AS clean
            WHERE clean.nconst IS NOT NULL
              AND clean.category IN ('actor', 'actress', 'director', 'writer')
            ORDER BY p.tconst, clean.nconst, clean.category, clean.ordering NULLS LAST
            """);

        statement.execute("""
            CREATE TEMP TABLE tmp_filtered_people ON COMMIT DROP AS
            SELECT DISTINCT
              fp.nconst,
              COALESCE(NULLIF(nb.primary_name, '\\N'), fp.nconst) AS primary_name
            FROM tmp_filtered_principals fp
            LEFT JOIN tmp_name_basics nb ON nb.nconst = fp.nconst
            WHERE fp.nconst IS NOT NULL
            """);

        statement.execute("""
            INSERT INTO person (nconst, primary_name)
            SELECT nconst, primary_name
            FROM tmp_filtered_people
            ON CONFLICT (nconst) DO UPDATE
              SET primary_name = EXCLUDED.primary_name
            """);

        statement.execute("""
            CREATE TEMP TABLE tmp_person_ids ON COMMIT DROP AS
            SELECT p.id, p.nconst
            FROM person p
            JOIN tmp_filtered_people fp ON fp.nconst = p.nconst
            """);

        statement.execute("""
            CREATE TEMP TABLE tmp_movie_ids ON COMMIT DROP AS
            SELECT id, tconst
            FROM movie
            WHERE tconst IN (SELECT tconst FROM tmp_selected_titles)
            """);

        statement.execute("""
            DELETE FROM movie_principal mp
            USING tmp_movie_ids mi
            WHERE mp.movie_id = mi.id
            """);

        statement.execute("""
            INSERT INTO movie_principal (movie_id, person_id, category, ordering, job, characters)
            SELECT DISTINCT
              mi.id,
              pi.id,
              fp.category,
              fp.ordering,
              fp.job,
              fp.characters
            FROM tmp_filtered_principals fp
            JOIN tmp_movie_ids mi ON mi.tconst = fp.tconst
            JOIN tmp_person_ids pi ON pi.nconst = fp.nconst
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
            Path nameBasics,
            Path titlePrincipals
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private Path titleBasics;
            private Path titleRatings;
            private Path nameBasics;
            private Path titlePrincipals;
            private Builder() {}

            public Builder titleBasics(Path path) {
                this.titleBasics = path;
                return this;
            }

            public Builder titleRatings(Path path) {
                this.titleRatings = path;
                return this;
            }

            public Builder nameBasics(Path path) {
                this.nameBasics = path;
                return this;
            }

            public Builder titlePrincipals(Path path) {
                this.titlePrincipals = path;
                return this;
            }

            public ImdbFiles build() {
                return new ImdbFiles(
                        Objects.requireNonNull(titleBasics, "titleBasics file is required"),
                        Objects.requireNonNull(titleRatings, "titleRatings file is required"),
                        Objects.requireNonNull(nameBasics, "nameBasics file is required"),
                        Objects.requireNonNull(titlePrincipals, "titlePrincipals file is required")
                );
            }
        }
    }
}
