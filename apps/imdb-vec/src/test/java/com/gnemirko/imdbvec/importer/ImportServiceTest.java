package com.gnemirko.imdbvec.importer;

import com.gnemirko.imdbvec.config.ImdbImportProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock
    private ImdbDownloader downloader;
    @Mock
    private ImdbCopyLoader loader;
    @Mock
    private ImdbDownloadMetadataStore metadataStore;

    private ImdbImportProperties properties;
    private ImportService importService;

    private static final List<String> ALL_FILES = List.of(
            "title.basics.tsv.gz", "title.ratings.tsv.gz", "name.basics.tsv.gz", "title.principals.tsv.gz");

    @BeforeEach
    void setUp() {
        properties = new ImdbImportProperties();
        properties.setBaseUrl("https://datasets.imdbws.com");
        properties.setDataDir(Path.of("./data/imdb"));
        properties.setMaxTitles(1000);
        importService = new ImportService(downloader, loader, properties, metadataStore);
    }

    private ImdbDownloader.DownloadResult resultFor(String file, boolean notModified) {
        return new ImdbDownloader.DownloadResult(
                properties.resolveDataPath(file), Optional.of("etag-" + file), Optional.empty(), notModified);
    }

    @Test
    void runFullImportThrowsWhenNoFilesConfigured() {
        properties.setFiles(List.of());

        assertThatThrownBy(() -> importService.runFullImport())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.imdb.files is empty");
    }

    @Test
    void runFullImportDownloadsEachFileAndLoadsWithResolvedPaths() throws Exception {
        properties.setFiles(ALL_FILES);
        for (String file : ALL_FILES) {
            when(metadataStore.load(properties.getDataDir(), file)).thenReturn(ImdbDownloadMetadataStore.Metadata.empty());
            when(downloader.downloadGzAtomically(eq(properties.resolveDownloadUri(file)), eq(properties.getDataDir()),
                    eq(file), any(), any())).thenReturn(resultFor(file, false));
        }
        when(loader.loadTopTitles(any(), anyInt())).thenReturn(42L);

        long inserted = importService.runFullImport();

        assertThat(inserted).isEqualTo(42L);
        ArgumentCaptor<ImdbCopyLoader.ImdbFiles> filesCaptor = ArgumentCaptor.forClass(ImdbCopyLoader.ImdbFiles.class);
        verify(loader).loadTopTitles(filesCaptor.capture(), eq(1000));
        ImdbCopyLoader.ImdbFiles files = filesCaptor.getValue();
        assertThat(files.titleBasics()).isEqualTo(properties.resolveDataPath("title.basics.tsv.gz"));
        assertThat(files.titleRatings()).isEqualTo(properties.resolveDataPath("title.ratings.tsv.gz"));
        assertThat(files.nameBasics()).isEqualTo(properties.resolveDataPath("name.basics.tsv.gz"));
        assertThat(files.titlePrincipals()).isEqualTo(properties.resolveDataPath("title.principals.tsv.gz"));
    }

    @Test
    void runFullImportStoresMetadataOnlyWhenDownloadWasNotConditionalHit() throws Exception {
        properties.setFiles(ALL_FILES);
        for (String file : ALL_FILES) {
            when(metadataStore.load(properties.getDataDir(), file)).thenReturn(ImdbDownloadMetadataStore.Metadata.empty());
        }
        when(downloader.downloadGzAtomically(eq(properties.resolveDownloadUri("title.basics.tsv.gz")), any(), eq("title.basics.tsv.gz"), any(), any()))
                .thenReturn(resultFor("title.basics.tsv.gz", true));
        when(downloader.downloadGzAtomically(eq(properties.resolveDownloadUri("title.ratings.tsv.gz")), any(), eq("title.ratings.tsv.gz"), any(), any()))
                .thenReturn(resultFor("title.ratings.tsv.gz", false));
        when(downloader.downloadGzAtomically(eq(properties.resolveDownloadUri("name.basics.tsv.gz")), any(), eq("name.basics.tsv.gz"), any(), any()))
                .thenReturn(resultFor("name.basics.tsv.gz", false));
        when(downloader.downloadGzAtomically(eq(properties.resolveDownloadUri("title.principals.tsv.gz")), any(), eq("title.principals.tsv.gz"), any(), any()))
                .thenReturn(resultFor("title.principals.tsv.gz", false));
        when(loader.loadTopTitles(any(), anyInt())).thenReturn(1L);

        importService.runFullImport();

        verify(metadataStore, never()).store(eq(properties.getDataDir()), eq("title.basics.tsv.gz"), any(), any());
        verify(metadataStore).store(eq(properties.getDataDir()), eq("title.ratings.tsv.gz"), any(), any());
        verify(metadataStore).store(eq(properties.getDataDir()), eq("name.basics.tsv.gz"), any(), any());
        verify(metadataStore).store(eq(properties.getDataDir()), eq("title.principals.tsv.gz"), any(), any());
    }

    @Test
    void runFullImportThrowsWhenARequiredFileIsMissingFromConfiguredList() throws Exception {
        List<String> partialFiles = List.of("title.basics.tsv.gz", "title.ratings.tsv.gz", "name.basics.tsv.gz");
        properties.setFiles(partialFiles);
        for (String file : partialFiles) {
            when(metadataStore.load(properties.getDataDir(), file)).thenReturn(ImdbDownloadMetadataStore.Metadata.empty());
            when(downloader.downloadGzAtomically(eq(properties.resolveDownloadUri(file)), any(), eq(file), any(), any()))
                    .thenReturn(resultFor(file, false));
        }

        assertThatThrownBy(() -> importService.runFullImport())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("title.principals.tsv.gz");
    }

    @Test
    void runFullImportPassesNegativeOneWhenMaxTitlesIsNull() throws Exception {
        properties.setFiles(ALL_FILES);
        properties.setMaxTitles(null);
        for (String file : ALL_FILES) {
            when(metadataStore.load(properties.getDataDir(), file)).thenReturn(ImdbDownloadMetadataStore.Metadata.empty());
            when(downloader.downloadGzAtomically(eq(properties.resolveDownloadUri(file)), any(), eq(file), any(), any()))
                    .thenReturn(resultFor(file, false));
        }
        when(loader.loadTopTitles(any(), anyInt())).thenReturn(0L);

        importService.runFullImport();

        verify(loader).loadTopTitles(any(), eq(-1));
    }
}
