package com.gnemirko.imdbvec.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Persists the remote ETag / Last-Modified headers that IMDb exposes so that subsequent
 * imports can use conditional GETs and skip re-downloading hundreds of megabytes when
 * nothing changed.
 */
@Component
public class ImdbDownloadMetadataStore {

    private static final Logger log = LoggerFactory.getLogger(ImdbDownloadMetadataStore.class);

    public record Metadata(Optional<String> etag, Optional<String> lastModified) {
        public static Metadata empty() {
            return new Metadata(Optional.empty(), Optional.empty());
        }
    }

    public Metadata load(Path dataDir, String fileName) {
        Objects.requireNonNull(dataDir, "dataDir must not be null");
        Objects.requireNonNull(fileName, "fileName must not be null");

        Path metaFile = metadataPath(dataDir, fileName);
        if (!Files.exists(metaFile)) {
            return Metadata.empty();
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(metaFile)) {
            props.load(in);
        } catch (IOException ex) {
            log.warn("Failed to read IMDb metadata for {} ({}); treating as empty.", fileName, metaFile, ex);
            return Metadata.empty();
        }

        return new Metadata(
                Optional.ofNullable(emptyToNull(props.getProperty("etag"))),
                Optional.ofNullable(emptyToNull(props.getProperty("lastModified")))
        );
    }

    public void store(Path dataDir, String fileName, Optional<String> etag, Optional<String> lastModified) throws IOException {
        Objects.requireNonNull(dataDir, "dataDir must not be null");
        Objects.requireNonNull(fileName, "fileName must not be null");

        Files.createDirectories(dataDir);
        Path metaFile = metadataPath(dataDir, fileName);

        Properties props = new Properties();
        etag.ifPresent(value -> props.setProperty("etag", value));
        lastModified.ifPresent(value -> props.setProperty("lastModified", value));

        try (OutputStream out = Files.newOutputStream(metaFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            props.store(out, "IMDb download metadata");
        }
    }

    private Path metadataPath(Path dataDir, String fileName) {
        return dataDir.resolve(fileName + ".meta");
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
