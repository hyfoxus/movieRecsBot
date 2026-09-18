package com.gnemirko.imdbvec.importer;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImdbDownloaderTest {

    private HttpServer server;
    private String baseUrl;
    private ImdbDownloader downloader;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
        downloader = new ImdbDownloader(WebClient.builder());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private byte[] gzipOf(String content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    @Test
    void downloadSavesGzipFileAndReturnsHeadersFromResponse(@TempDir Path dataDir) throws IOException {
        byte[] gz = gzipOf("tconst\ttitleType\nvalue\tmovie\n");
        server.createContext("/title.basics.tsv.gz", exchange -> {
            exchange.getResponseHeaders().add("ETag", "\"v1\"");
            exchange.getResponseHeaders().add("Last-Modified", "Wed, 01 Jan 2025 00:00:00 GMT");
            exchange.sendResponseHeaders(200, gz.length);
            exchange.getResponseBody().write(gz);
            exchange.close();
        });

        ImdbDownloader.DownloadResult result = downloader.downloadGzAtomically(
                URI.create(baseUrl + "/title.basics.tsv.gz"), dataDir, "title.basics.tsv.gz", Optional.empty(), Optional.empty());

        assertThat(result.notModified()).isFalse();
        assertThat(result.etag()).contains("\"v1\"");
        assertThat(result.lastModified()).contains("Wed, 01 Jan 2025 00:00:00 GMT");
        assertThat(Files.exists(dataDir.resolve("title.basics.tsv.gz"))).isTrue();
        assertThat(Files.exists(dataDir.resolve("title.basics.tsv.gz.part"))).isFalse();
    }

    @Test
    void downloadReturnsNotModifiedWithoutWritingFileWhen304(@TempDir Path dataDir) throws IOException {
        server.createContext("/title.ratings.tsv.gz", exchange -> {
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
        });

        ImdbDownloader.DownloadResult result = downloader.downloadGzAtomically(
                URI.create(baseUrl + "/title.ratings.tsv.gz"), dataDir, "title.ratings.tsv.gz",
                Optional.of("\"cached\""), Optional.of("Tue, 31 Dec 2024 00:00:00 GMT"));

        assertThat(result.notModified()).isTrue();
        assertThat(Files.exists(dataDir.resolve("title.ratings.tsv.gz"))).isFalse();
    }

    @Test
    void downloadSendsConditionalHeadersWhenEtagAndLastModifiedProvided(@TempDir Path dataDir) throws IOException {
        server.createContext("/name.basics.tsv.gz", exchange -> {
            String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
            String ifModifiedSince = exchange.getRequestHeaders().getFirst("If-Modified-Since");
            boolean sentBoth = "\"tag\"".equals(ifNoneMatch) && "Mon, 01 Jan 2024 00:00:00 GMT".equals(ifModifiedSince);
            exchange.sendResponseHeaders(sentBoth ? 304 : 500, -1);
            exchange.close();
        });

        ImdbDownloader.DownloadResult result = downloader.downloadGzAtomically(
                URI.create(baseUrl + "/name.basics.tsv.gz"), dataDir, "name.basics.tsv.gz",
                Optional.of("\"tag\""), Optional.of("Mon, 01 Jan 2024 00:00:00 GMT"));

        assertThat(result.notModified()).isTrue();
    }

    @Test
    void downloadThrowsAndDeletesPartFileOnNonSuccessStatus(@TempDir Path dataDir) {
        server.createContext("/title.principals.tsv.gz", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        assertThatThrownBy(() -> downloader.downloadGzAtomically(
                URI.create(baseUrl + "/title.principals.tsv.gz"), dataDir, "title.principals.tsv.gz",
                Optional.empty(), Optional.empty()))
                .hasMessageContaining("HTTP 500");

        assertThat(Files.exists(dataDir.resolve("title.principals.tsv.gz.part"))).isFalse();
        assertThat(Files.exists(dataDir.resolve("title.principals.tsv.gz"))).isFalse();
    }

    @Test
    void downloadDeletesPartFileWhenBodyFailsGzipHeaderCheck(@TempDir Path dataDir) throws IOException {
        byte[] notGzipped = "this is not gzip content".getBytes(StandardCharsets.UTF_8);
        server.createContext("/bad.tsv.gz", exchange -> {
            exchange.sendResponseHeaders(200, notGzipped.length);
            exchange.getResponseBody().write(notGzipped);
            exchange.close();
        });

        assertThatThrownBy(() -> downloader.downloadGzAtomically(
                URI.create(baseUrl + "/bad.tsv.gz"), dataDir, "bad.tsv.gz", Optional.empty(), Optional.empty()))
                .hasMessageContaining("gzip header check");

        assertThat(Files.exists(dataDir.resolve("bad.tsv.gz.part"))).isFalse();
        assertThat(Files.exists(dataDir.resolve("bad.tsv.gz"))).isFalse();
    }

    @Test
    void downloadCreatesDataDirectoryIfMissing(@TempDir Path tempDir) throws IOException {
        Path dataDir = tempDir.resolve("does/not/exist/yet");
        byte[] gz = gzipOf("content");
        server.createContext("/small.tsv.gz", exchange -> {
            exchange.sendResponseHeaders(200, gz.length);
            exchange.getResponseBody().write(gz);
            exchange.close();
        });

        downloader.downloadGzAtomically(URI.create(baseUrl + "/small.tsv.gz"), dataDir, "small.tsv.gz",
                Optional.empty(), Optional.empty());

        assertThat(Files.exists(dataDir.resolve("small.tsv.gz"))).isTrue();
    }
}
