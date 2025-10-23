package com.gnemirko.imdbvec.importer;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.zip.GZIPInputStream;


@Component
public class ImdbCopyLoader {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public ImdbCopyLoader(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    public long loadTitleBasics(Path gzFile) throws Exception {
        Connection c = DataSourceUtils.getConnection(dataSource);

        try (var st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS tmp_title_basics");
            st.execute("""
                CREATE TEMP TABLE tmp_title_basics (
                  tconst           text,
                  title_type       text,
                  primary_title    text,
                  original_title   text,
                  is_adult         boolean,
                  start_year       smallint,
                  end_year         smallint,
                  runtime_minutes  smallint,
                  genres           text
                ) ON COMMIT DROP
                """);

            CopyManager cm = c.unwrap(PGConnection.class).getCopyAPI();

            try (InputStream fis  = new FileInputStream(gzFile.toFile());
                 InputStream bis  = new BufferedInputStream(fis);
                 GZIPInputStream gz = new GZIPInputStream(bis)) {

                skipHeaderLine(gz);

                cm.copyIn("""
                    COPY tmp_title_basics
                      (tconst, title_type, primary_title, original_title, is_adult,
                       start_year, end_year, runtime_minutes, genres)
                    FROM STDIN WITH (FORMAT csv, DELIMITER E'\\t', NULL '\\\\N')
                    """, gz);
            }

            long inserted = st.executeUpdate("""
                INSERT INTO movie
                  (tconst, title_type, primary_title, original_title, is_adult,
                   start_year, end_year, runtime_minutes, genres)
                SELECT
                  tconst, title_type, primary_title, original_title, is_adult,
                  start_year, end_year, runtime_minutes,
                  CASE
                    WHEN genres IS NULL THEN NULL
                    ELSE string_to_array(genres, ',')::text[]
                  END
                FROM tmp_title_basics
                """);

            st.execute("DROP TABLE IF EXISTS tmp_title_basics");
            return inserted;
        } finally {
            DataSourceUtils.releaseConnection(c, dataSource);
        }
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
}
