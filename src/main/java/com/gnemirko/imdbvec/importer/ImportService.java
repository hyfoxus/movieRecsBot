package com.gnemirko.imdbvec.importer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Service
public class ImportService {

    private final ImdbDownloader downloader;
    private final ImdbCopyLoader loader;
    private final JdbcTemplate jdbc;
    private final List<String> files;

    public ImportService(ImdbDownloader downloader,
                         ImdbCopyLoader loader,
                         JdbcTemplate jdbc,
                         @Value("${app.imdb.files}") List<String> files) {
        this.downloader = downloader;
        this.loader = loader;
        this.jdbc = jdbc;
        this.files = files;
    }

    @Transactional
    public void runFullImport() throws Exception {
        jdbc.execute("SET statement_timeout = 0");
        truncateTables();

        for (String f : files) {
            jdbc.update("""
                INSERT INTO import_log (file_name, rows_loaded)
                VALUES (?, 0)
                ON CONFLICT (file_name) DO NOTHING
                """, f);

            Map<String, Object> row = jdbc.query(
                    "SELECT etag, last_modified, rows_loaded FROM import_log WHERE file_name = ? FOR UPDATE SKIP LOCKED",
                    ps -> ps.setString(1, f),
                    rs -> {
                        if (!rs.next()) return null;
                        Map<String, Object> map = new HashMap<>();
                        map.put("etag", rs.getString("etag"));
                        map.put("last_modified", rs.getString("last_modified"));
                        map.put("rows_loaded", rs.getLong("rows_loaded"));
                        return map;
                    }
            );
            String prevEtag = row == null ? null : (String) row.get("etag");
            String prevLm   = row == null ? null : (String) row.get("last_modified");

            ImdbDownloader.DownloadResult download = downloader.downloadIfChanged(f, prevEtag, prevLm);
            Path path = download.path();

            long loaded = switch (f) {
                case "title.basics.tsv.gz"     -> loader.loadTitleBasics(path);
                case "title.ratings.tsv.gz"    -> loader.loadTitleRatings(path);
                case "title.akas.tsv.gz"       -> loader.loadTitleAkas(path);
                case "title.principals.tsv.gz" -> loader.loadTitlePrincipals(path);
                case "title.crew.tsv.gz"       -> loader.loadTitleCrew(path);
                case "title.episode.tsv.gz"    -> loader.loadTitleEpisode(path);
                case "name.basics.tsv.gz"      -> loader.loadNameBasics(path);
                default -> 0L;
            };

            // We don't have ETag/L-M here because WebClient .toEntity() gave us only once; in practice,
            // you might read headers in downloader and return them. For demo, mark imported rows.
            jdbc.update("""
                INSERT INTO import_log(file_name, etag, last_modified, imported_at, rows_loaded)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (file_name) DO UPDATE
                SET imported_at = EXCLUDED.imported_at,
                    rows_loaded = EXCLUDED.rows_loaded,
                    etag = EXCLUDED.etag,
                    last_modified = EXCLUDED.last_modified
                """, f, download.etag(), download.lastModified(), Timestamp.from(Instant.now()), loaded);
        }

        // Normalize booleans and arrays if needed (IMDb 'isAdult' is '0|1' in TSV)
        jdbc.update("UPDATE movie SET is_adult = (is_adult::text IN ('1','t','true')) WHERE is_adult IS NOT NULL");
        jdbc.update("UPDATE movie SET genres = NULL WHERE genres = ARRAY['\\N']");
    }

    private void truncateTables() {
        jdbc.execute("TRUNCATE TABLE title_akas, title_principals, title_crew, title_episode, person, movie RESTART IDENTITY CASCADE");
    }
}
