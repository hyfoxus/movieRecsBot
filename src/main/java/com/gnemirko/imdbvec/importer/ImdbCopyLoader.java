package com.gnemirko.imdbvec.importer;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.*;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.zip.GZIPInputStream;

@Component
public class ImdbCopyLoader {

    private final DataSource ds;

    public ImdbCopyLoader(DataSource ds) { this.ds = ds; }

    private long copyTsvGz(Path gzPath, String copySql, boolean header) throws SQLException, IOException {
        Connection c = DataSourceUtils.getConnection(ds);
        try {
            CopyManager cm = c.unwrap(PGConnection.class).getCopyAPI();
            try (InputStream in = new GZIPInputStream(new BufferedInputStream(new FileInputStream(gzPath.toFile())))) {
                InputStream data = header ? skipFirstLine(in) : in;
                return cm.copyIn(copySql, data);
            }
        } finally {
            DataSourceUtils.releaseConnection(c, ds);
        }
    }

    private InputStream skipFirstLine(InputStream in) throws IOException {
        int b; boolean seenLF = false;
        while ((b = in.read()) != -1) {
            if (b == '\n') { seenLF = true; break; }
        }
        if (!seenLF) return in; // empty?
        return in;
    }

    public long loadTitleBasics(Path gz) throws Exception {
        Connection c = DataSourceUtils.getConnection(ds);
        try (var st = c.createStatement()) {
            st.execute("""
                CREATE TEMP TABLE tmp_title_basics (
                  tconst text,
                  title_type text,
                  primary_title text,
                  original_title text,
                  is_adult boolean,
                  start_year smallint,
                  end_year smallint,
                  runtime_minutes smallint,
                  genres text
                )
                """);
            CopyManager cm = c.unwrap(PGConnection.class).getCopyAPI();
            try (InputStream in = new GZIPInputStream(new BufferedInputStream(new FileInputStream(gz.toFile())))) {
                skipHeader(in);
                cm.copyIn("""
                    COPY tmp_title_basics (tconst, title_type, primary_title, original_title, is_adult, start_year, end_year, runtime_minutes, genres)
                    FROM STDIN WITH (FORMAT csv, DELIMITER E'\\t', NULL '\\N')
                    """, in);
            }
            var inserted = st.executeUpdate("""
                INSERT INTO movie (tconst, title_type, primary_title, original_title, is_adult, start_year, end_year, runtime_minutes, genres)
                SELECT
                  tconst, title_type, primary_title, original_title, is_adult, start_year, end_year, runtime_minutes,
                  CASE
                    WHEN genres IS NULL THEN NULL
                    ELSE string_to_array(genres, ',')::text[]
                  END
                FROM tmp_title_basics
                """);
            st.execute("DROP TABLE tmp_title_basics");
            return inserted;
        } finally {
            DataSourceUtils.releaseConnection(c, ds);
        }
    }

    public long loadTitleRatings(Path gz) throws Exception {
        Connection c = DataSourceUtils.getConnection(ds);
        try (var st = c.createStatement()) {
            st.execute("CREATE TEMP TABLE tmp_ratings(tconst text, rating double precision, votes integer)");
            CopyManager cm = c.unwrap(PGConnection.class).getCopyAPI();
            try (InputStream in = new GZIPInputStream(new FileInputStream(gz.toFile()))) {
                skipHeader(in);
                cm.copyIn("COPY tmp_ratings FROM STDIN WITH (FORMAT csv, DELIMITER E'\\t', NULL '\\N')", in);
            }
            var up = st.executeUpdate("""
                UPDATE movie m SET rating = r.rating, votes = r.votes
                FROM tmp_ratings r WHERE r.tconst = m.tconst
                """);
            st.execute("DROP TABLE tmp_ratings");
            return up;
        } finally {
            DataSourceUtils.releaseConnection(c, ds);
        }
    }

    public long loadTitleAkas(Path gz) throws Exception {
        String sql = """
            COPY title_akas (tconst, ordering, title, region, language, types, attributes, is_original_title)
            FROM STDIN WITH (FORMAT csv, DELIMITER E'\\t', NULL '\\N')
            """;
        return copyTsvGz(gz, sql, true);
    }

    public long loadTitlePrincipals(Path gz) throws Exception {
        String sql = """
            COPY title_principals (tconst, ordering, nconst, category, job, characters)
            FROM STDIN WITH (FORMAT csv, DELIMITER E'\\t', NULL '\\N')
            """;
        return copyTsvGz(gz, sql, true);
    }

    public long loadTitleCrew(Path gz) throws Exception {
        String sql = """
            COPY title_crew (tconst, directors, writers)
            FROM STDIN WITH (FORMAT csv, DELIMITER E'\\t', NULL '\\N')
            """;
        return copyTsvGz(gz, sql, true);
    }

    public long loadTitleEpisode(Path gz) throws Exception {
        String sql = """
            COPY title_episode (tconst, parent_tconst, season_number, episode_number)
            FROM STDIN WITH (FORMAT csv, DELIMITER E'\\t', NULL '\\N')
            """;
        return copyTsvGz(gz, sql, true);
    }

    public long loadNameBasics(Path gz) throws Exception {
        String sql = """
            COPY person (nconst, primary_name, birth_year, death_year, primary_profession)
            FROM STDIN WITH (FORMAT csv, DELIMITER E'\\t', NULL '\\N')
            """;
        return copyTsvGz(gz, sql, true);
    }

    private void skipHeader(InputStream in) throws IOException {
        int b; while ((b = in.read()) != -1) { if (b == '\n') break; }
    }
}
