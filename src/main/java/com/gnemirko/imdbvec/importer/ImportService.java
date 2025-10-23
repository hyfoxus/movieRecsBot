package com.gnemirko.imdbvec.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

@Service
public class ImportService {
    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final ImdbDownloader downloader;
    private final ImdbCopyLoader loader;
    private final JdbcTemplate jdbc;

    private final Path dataDir;
    private final URI titleBasicsUrl = URI.create("https://datasets.imdbws.com/title.basics.tsv.gz");

    public ImportService(ImdbDownloader downloader, ImdbCopyLoader loader, JdbcTemplate jdbc) {
        this.downloader = downloader;
        this.loader = loader;
        this.jdbc = jdbc;
        this.dataDir = Path.of("/data/imdb");
    }

    @Transactional
    public long runFullImport() throws Exception {
        log.info("IMDb full import started at {}", Instant.now());

        Optional<String> etag = Optional.empty();
        Optional<String> lastMod = Optional.empty();

        var res = downloader.downloadGzAtomically(titleBasicsUrl, dataDir, "title.basics.tsv.gz", etag, lastMod);
        if (res.notModified()) {
            log.info("title.basics.tsv.gz not modified; skipping reload");
            return 0L;
        }

        jdbc.execute("DROP TABLE IF EXISTS tmp_title_basics");

        long ins = loader.loadTitleBasics(res.file());



        log.info("IMDb import finished. Inserted rows: {}", ins);
        return ins;
    }
}