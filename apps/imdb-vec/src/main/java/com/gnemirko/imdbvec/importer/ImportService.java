package com.gnemirko.imdbvec.importer;

import com.gnemirko.imdbvec.config.ImdbImportProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImportService {
    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private static final String TITLE_BASICS_FILE = "title.basics.tsv.gz";
    private static final String TITLE_RATINGS_FILE = "title.ratings.tsv.gz";
    private static final String TITLE_AKAS_FILE = "title.akas.tsv.gz";
    private static final String TITLE_CREW_FILE = "title.crew.tsv.gz";
    private static final String TITLE_PRINCIPALS_FILE = "title.principals.tsv.gz";
    private static final String TITLE_EPISODE_FILE = "title.episode.tsv.gz";
    private static final String NAME_BASICS_FILE = "name.basics.tsv.gz";
    private final ImdbDownloader downloader;
    private final ImdbCopyLoader loader;
    private final JdbcTemplate jdbc;
    private final ImdbImportProperties properties;

    public ImportService(ImdbDownloader downloader,
                         ImdbCopyLoader loader,
                         JdbcTemplate jdbc,
                         ImdbImportProperties properties) {
        this.downloader = downloader;
        this.loader = loader;
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Transactional
    public long runFullImport() throws Exception {
        log.info("IMDb full import started at {}", Instant.now());

        Map<String, ImdbDownloader.DownloadResult> results = new HashMap<>();
        List<String> filesToFetch = properties.getFiles();
        if (filesToFetch.isEmpty()) {
            throw new IllegalStateException("No IMDb files configured (app.imdb.files is empty)");
        }

        for (String file : filesToFetch) {
            var url = properties.resolveDownloadUri(file);
            Path targetDir = properties.getDataDir();
            Optional<String> etag = Optional.empty();
            Optional<String> lastMod = Optional.empty();

            log.info("Downloading {} from {}", file, url);
            ImdbDownloader.DownloadResult download = downloader.downloadGzAtomically(
                    url,
                    targetDir,
                    file,
                    etag,
                    lastMod
            );
            results.put(file, download);
        }

        ImdbCopyLoader.ImdbFiles files = ImdbCopyLoader.ImdbFiles.builder()
                .titleBasics(resolveOrThrow(results, TITLE_BASICS_FILE))
                .titleRatings(resolveOrThrow(results, TITLE_RATINGS_FILE))
                .titleAkas(resolveOrThrow(results, TITLE_AKAS_FILE))
                .titleCrew(resolveOrThrow(results, TITLE_CREW_FILE))
                .titlePrincipals(resolveOrThrow(results, TITLE_PRINCIPALS_FILE))
                .titleEpisode(resolveOrThrow(results, TITLE_EPISODE_FILE))
                .nameBasics(resolveOrThrow(results, NAME_BASICS_FILE))
                .build();

        Integer maxTitles = properties.getMaxTitles();
        long inserted = loader.loadTopTitles(files, maxTitles == null ? -1 : maxTitles);

        log.info("IMDb import finished. Upserted rows: {}", inserted);
        return inserted;
    }

    private Path resolveOrThrow(Map<String, ImdbDownloader.DownloadResult> results, String fileName) {
        ImdbDownloader.DownloadResult result = results.get(fileName);
        if (result == null) {
            throw new IllegalStateException("Missing required IMDb file: " + fileName);
        }
        return result.file();
    }
}
