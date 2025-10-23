package com.gnemirko.imdbvec.importer;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
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

    public ImdbCopyLoader(DataSource dataSource) {
        this.dataSource = dataSource;
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
                  is_adult         text,
                  start_year       text,
                  end_year         text,
                  runtime_minutes  text,
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
                    FROM STDIN WITH (FORMAT text)
                    """, gz);
            }

            long inserted = st.executeUpdate("""
                INSERT INTO movie
                  (tconst, title_type, primary_title, original_title, is_adult,
                   start_year, end_year, runtime_minutes, genres)
                SELECT
                  tconst,
                  NULLIF(title_type, '\\N'),
                  NULLIF(primary_title, '\\N'),
                  NULLIF(original_title, '\\N'),
                  CASE
                    WHEN is_adult IS NULL OR is_adult = '\\N' THEN NULL
                    WHEN is_adult IN ('1','t','true','TRUE') THEN TRUE
                    WHEN is_adult IN ('0','f','false','FALSE') THEN FALSE
                    ELSE NULL
                  END,
                  NULLIF(start_year, '\\N')::smallint,
                  NULLIF(end_year, '\\N')::smallint,
                  CASE
                    WHEN runtime_minutes IS NULL OR runtime_minutes = '\\N' THEN NULL
                    WHEN runtime_minutes !~ '^-?\\d+$' THEN NULL
                    WHEN (runtime_minutes)::integer BETWEEN -32768 AND 32767
                      THEN runtime_minutes::smallint
                    ELSE NULL
                  END,
                  CASE
                    WHEN genres IS NULL OR genres = '\\N' THEN NULL
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
