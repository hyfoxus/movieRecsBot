package com.gnemirko.imdbvec.importer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

@Component
public class ImdbDownloader {

    private final WebClient web;
    private final String baseUrl;
    private final Path dataDir;

    public ImdbDownloader(@Qualifier("plainWebClient") WebClient plainWebClient,
                          @Value("${app.imdb.baseUrl}") String baseUrl,
                          @Value("${app.imdb.dataDir}") String dataDir) throws IOException {
        this.web = plainWebClient;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length()-1) : baseUrl;
        this.dataDir = Paths.get(dataDir);
        Files.createDirectories(this.dataDir);
    }

    public DownloadResult downloadIfChanged(String fileName, String etagPrev, String lmPrev) throws IOException {
        return download(fileName, etagPrev, lmPrev, false);
    }

    public DownloadResult downloadFresh(String fileName) throws IOException {
        return download(fileName, null, null, true);
    }

    private DownloadResult download(String fileName, String etagPrev, String lmPrev, boolean force) throws IOException {
        String uri = baseUrl + "/" + fileName;

        Path out = dataDir.resolve(fileName);
        String etag = null;
        String lastModified = null;

        if (!force) {
            ClientResponse headResponse = web.head()
                    .uri(uri)
                    .exchangeToMono(Mono::just)
                    .block();

            if (headResponse == null) {
                throw new IOException("Failed to fetch headers for " + uri);
            }

            HttpStatusCode headStatus = headResponse.statusCode();
            if (!headStatus.is2xxSuccessful()) {
                try {
                    headResponse.releaseBody().block(Duration.ofSeconds(30));
                } catch (Exception ignored) {}
                throw new IOException("Failed to fetch headers for " + uri + " status=" + headStatus.value());
            }

            etag = headResponse.headers().asHttpHeaders().getFirst(HttpHeaders.ETAG);
            lastModified = headResponse.headers().asHttpHeaders().getFirst(HttpHeaders.LAST_MODIFIED);

            boolean unchanged = Files.exists(out)
                    && etag != null && lastModified != null
                    && etag.equals(etagPrev)
                    && lastModified.equals(lmPrev);

            if (unchanged) {
                return new DownloadResult(out, etag, lastModified, true);
            }
        }

        ClientResponse getResponse = web.get()
                .uri(uri)
                .exchangeToMono(Mono::just)
                .block();

        if (getResponse == null) {
            throw new IOException("Failed to download " + uri);
        }

        if (!getResponse.statusCode().is2xxSuccessful()) {
            try {
                getResponse.releaseBody().block(Duration.ofSeconds(30));
            } catch (Exception ignored) {}
            throw new IOException("Failed to download " + uri + " status=" + getResponse.statusCode().value());
        }

        Flux<DataBuffer> body = getResponse.bodyToFlux(DataBuffer.class);

        if (etag == null) {
            etag = getResponse.headers().asHttpHeaders().getFirst(HttpHeaders.ETAG);
        }
        if (lastModified == null) {
            lastModified = getResponse.headers().asHttpHeaders().getFirst(HttpHeaders.LAST_MODIFIED);
        }

        DataBufferUtils.write(body, out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
                .block(Duration.ofHours(1));

        return new DownloadResult(out, etag, lastModified, false);
    }


    public record DownloadResult(Path path, String etag, String lastModified, boolean unchanged) {
    }
}
