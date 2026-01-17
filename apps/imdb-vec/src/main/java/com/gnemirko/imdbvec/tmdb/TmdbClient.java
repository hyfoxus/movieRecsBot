package com.gnemirko.imdbvec.tmdb;

import com.gnemirko.imdbvec.config.TmdbProperties;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Optional;

@Component
public class TmdbClient {

    private static final Logger log = LoggerFactory.getLogger(TmdbClient.class);

    private final WebClient webClient;
    private final TmdbProperties properties;

    public TmdbClient(TmdbProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.create().responseTimeout(properties.getReadTimeout());
        Duration connectTimeout = properties.getConnectTimeout();
        if (connectTimeout != null && !connectTimeout.isNegative()) {
            httpClient = httpClient.option(
                    ChannelOption.CONNECT_TIMEOUT_MILLIS,
                    Math.toIntExact(Math.max(1L, connectTimeout.toMillis()))
            );
        }
        this.webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public boolean isEnabled() {
        return properties.isEnabled() && StringUtils.hasText(properties.getApiKey());
    }

    public Optional<String> fetchOverview(String imdbId) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        if (!StringUtils.hasText(imdbId)) {
            return Optional.empty();
        }
        String trimmed = imdbId.trim();
        int attempts = Math.max(1, properties.getMaxRetries());
        Duration delay = properties.getRetryDelay();
        if (delay == null || delay.isNegative()) {
            delay = Duration.ofSeconds(1);
        }
        Retry retry = Retry.fixedDelay(attempts, delay)
                .filter(this::isRetryable)
                .onRetryExhaustedThrow((spec, signal) ->
                        signal.failure() == null ? new IllegalStateException("TMDB retries exhausted") : signal.failure());
        try {
            TmdbFindResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/find/{externalId}")
                            .queryParam("external_source", "imdb_id")
                            .queryParam("language", properties.getLanguage())
                            .queryParam("api_key", properties.getApiKey().trim())
                            .build(trimmed))
                    .retrieve()
                    .bodyToMono(TmdbFindResponse.class)
                    .retryWhen(retry)
                    .block();
            if (response == null) {
                log.debug("TMDB returned null for {}", trimmed);
                return Optional.empty();
            }
            return response.movieResults().stream()
                    .map(TmdbMovieResult::overview)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .findFirst();
        } catch (WebClientResponseException.NotFound nf) {
            log.debug("TMDB returned 404 for {}", trimmed);
            return Optional.empty();
        } catch (WebClientResponseException wcre) {
            if (wcre.getStatusCode().value() == 401) {
                log.error("TMDB API key rejected. Verify app.tmdb.api-key configuration.");
                return Optional.empty();
            }
            throw wcre;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to fetch TMDB overview for " + trimmed, ex);
        }
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException response) {
            int status = response.getStatusCode().value();
            return status == 429 || (status >= 500 && status < 600);
        }
        return throwable instanceof WebClientRequestException;
    }
}
