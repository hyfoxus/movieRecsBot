package com.gnemirko.imdbvec.importer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ImdbDownloadMetadataStoreTest {

    private final ImdbDownloadMetadataStore store = new ImdbDownloadMetadataStore();

    @Test
    void loadReturnsEmptyWhenNoMetadataFileExists(@TempDir Path dir) {
        ImdbDownloadMetadataStore.Metadata metadata = store.load(dir, "title.basics.tsv.gz");

        assertThat(metadata.etag()).isEmpty();
        assertThat(metadata.lastModified()).isEmpty();
    }

    @Test
    void storeThenLoadRoundTripsEtagAndLastModified(@TempDir Path dir) throws IOException {
        store.store(dir, "title.basics.tsv.gz", Optional.of("\"abc123\""), Optional.of("Wed, 01 Jan 2025 00:00:00 GMT"));

        ImdbDownloadMetadataStore.Metadata metadata = store.load(dir, "title.basics.tsv.gz");

        assertThat(metadata.etag()).contains("\"abc123\"");
        assertThat(metadata.lastModified()).contains("Wed, 01 Jan 2025 00:00:00 GMT");
    }

    @Test
    void storeOnlyPersistsValuesThatArePresent(@TempDir Path dir) throws IOException {
        store.store(dir, "title.ratings.tsv.gz", Optional.of("\"etag-only\""), Optional.empty());

        ImdbDownloadMetadataStore.Metadata metadata = store.load(dir, "title.ratings.tsv.gz");

        assertThat(metadata.etag()).contains("\"etag-only\"");
        assertThat(metadata.lastModified()).isEmpty();
    }

    @Test
    void storeCreatesMissingParentDirectories(@TempDir Path dir) throws IOException {
        Path nested = dir.resolve("nested/sub");

        store.store(nested, "name.basics.tsv.gz", Optional.of("\"e\""), Optional.empty());

        assertThat(Files.exists(nested.resolve("name.basics.tsv.gz.meta"))).isTrue();
    }

    @Test
    void loadTreatsBlankStoredValuesAsAbsent(@TempDir Path dir) throws IOException {
        Path metaFile = dir.resolve("title.principals.tsv.gz.meta");
        Files.writeString(metaFile, "etag=\nlastModified=\n");

        ImdbDownloadMetadataStore.Metadata metadata = store.load(dir, "title.principals.tsv.gz");

        assertThat(metadata.etag()).isEmpty();
        assertThat(metadata.lastModified()).isEmpty();
    }

    @Test
    void loadReturnsEmptyWithoutThrowingWhenMetadataFileIsUnreadable(@TempDir Path dir) throws IOException {
        // Create a directory where a regular file is expected, so Files.newInputStream fails with IOException.
        Path metaPath = dir.resolve("title.basics.tsv.gz.meta");
        Files.createDirectory(metaPath);

        ImdbDownloadMetadataStore.Metadata metadata = store.load(dir, "title.basics.tsv.gz");

        assertThat(metadata).isEqualTo(ImdbDownloadMetadataStore.Metadata.empty());
    }

    @Test
    void metadataEmptyFactoryReturnsBothEmptyOptionals() {
        ImdbDownloadMetadataStore.Metadata metadata = ImdbDownloadMetadataStore.Metadata.empty();

        assertThat(metadata.etag()).isEmpty();
        assertThat(metadata.lastModified()).isEmpty();
    }
}
