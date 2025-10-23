package com.gnemirko.imdbvec.importer;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.Optional;
import java.util.zip.GZIPInputStream;


@Component
public class ImdbDownloader {

    private final WebClient http;

    public ImdbDownloader(WebClient.Builder builder) {
        // Dedicated client with sane timeouts
        HttpClient netty = HttpClient.create()
                .responseTimeout(Duration.ofMinutes(5))
                .compress(true);

        this.http = builder
                .clientConnector(new ReactorClientHttpConnector(netty))
                .build();
    }

    public record DownloadResult(Path file, Optional<String> etag, Optional<String> lastModified, boolean notModified) {}


    public DownloadResult downloadGzAtomically(URI url,
                                               Path dataDir,
                                               String fileName,
                                               Optional<String> etag,
                                               Optional<String> lastModified) throws IOException {

        Files.createDirectories(dataDir);

        Path finalFile = dataDir.resolve(fileName);
        Path partFile  = dataDir.resolve(fileName + ".part");
        try { Files.deleteIfExists(partFile); } catch (Exception ignore) {}

        WebClient.RequestHeadersSpec<?> req = http.get().uri(url);
        if (etag.isPresent())         req = req.header(HttpHeaders.IF_NONE_MATCH, etag.get());
        if (lastModified.isPresent()) req = req.header(HttpHeaders.IF_MODIFIED_SINCE, lastModified.get());
        req = req.header(HttpHeaders.ACCEPT_ENCODING, "identity");

        DownloadResult result = req.exchangeToMono(cr -> {
            HttpStatus status = (HttpStatus) cr.statusCode();
            if (status == HttpStatus.NOT_MODIFIED) {
                return Mono.just(new DownloadResult(finalFile, etag, lastModified, true));
            }
            if (!status.is2xxSuccessful()) {
                return Mono.error(new IOException("HTTP " + status + " for " + url));
            }

            var newEtag = Optional.ofNullable(cr.headers().asHttpHeaders().getFirst(HttpHeaders.ETAG));
            var newLM   = Optional.ofNullable(cr.headers().asHttpHeaders().getFirst(HttpHeaders.LAST_MODIFIED));

            return DataBufferUtils.write(
                            cr.bodyToFlux(DataBuffer.class),
                            partFile,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE
                    )
                    .doOnError(e -> {
                        try { Files.deleteIfExists(partFile); } catch (Exception ignore) {}
                    })
                    .then(Mono.fromCallable(() -> {
                        try (var in = new GZIPInputStream(Files.newInputStream(partFile))) {
                        } catch (Exception e) {
                            try { Files.deleteIfExists(partFile); } catch (Exception ignore) {}
                            throw new IOException("Downloaded file failed gzip header check: " + partFile, e);
                        }

                        Files.move(partFile, finalFile,
                                StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);

                        return new DownloadResult(finalFile, newEtag, newLM, false);
                    }));
        }).block(Duration.ofMinutes(10));

        if (result == null) throw new IOException("No response from " + url);
        return result;
    }

}