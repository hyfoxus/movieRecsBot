package com.gnemirko.imdbvec.service;

import com.gnemirko.imdbvec.model.Movie;
import com.gnemirko.imdbvec.repo.MovieRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * EmbeddingService implemented via the local Ollama Embeddings API.
 * Public API stays the same: float[] embed(String text)
 *
 * Requires Ollama running locally and an embedding model pulled, e.g.:
 *   ollama pull nomic-embed-text
 * API ref: https://docs.ollama.com/api  (see /api/embeddings)
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final WebClient web;
    private final String model;
    private final String baseUrl;
    private final MovieRepository movies;
    private final int maxRetries;
    private final Duration retryDelay;

    public EmbeddingService(
            @Value("${app.ollama.baseUrl:http://localhost:11434}") String baseUrl,
            @Value("${app.ollama.embeddingModel:nomic-embed-text}") String model,
            @Value("${app.ollama.embeddingMaxRetries:3}") int maxRetries,
            @Value("${app.ollama.embeddingRetryDelay:PT5S}") Duration retryDelay,
            MovieRepository movies
    ) {
        this.baseUrl = baseUrl;
        this.web = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .build();
        this.model = model;
        this.movies = movies;
        this.maxRetries = Math.max(1, maxRetries);
        this.retryDelay = retryDelay.isNegative() ? Duration.ZERO : retryDelay;
    }

    /** Compute a single embedding vector for the given text. */
    public float[] embed(String text) {
        Map<String, Object> request = Map.of(
                "model", model,
                "prompt", text == null ? "" : text,
                "stream", false
        );

        Duration backoff = retryDelay.isZero() ? Duration.ofMillis(250) : retryDelay;
        int attempts = Math.max(1, maxRetries);
        Retry retrySpec = Retry.backoff(Math.max(1, attempts), backoff)
                .filter(this::isRetryableException)
                .doBeforeRetry(signal -> log.warn(
                        "Embedding retry {}/{} after {} due to {}",
                        signal.totalRetries() + 1,
                        attempts,
                        backoff,
                        signal.failure() == null ? "unknown error" : signal.failure().getMessage()))
                .onRetryExhaustedThrow((spec, signal) ->
                        signal.failure() != null ? signal.failure() : new IllegalStateException("Embedding retries exhausted"));

        try {
            Map<String, Object> response = web.post()
                    .uri("/api/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .retryWhen(retrySpec)
                    .block();

            if (response == null || !response.containsKey("embedding")) {
                throw new IllegalStateException("Ollama embeddings API returned no 'embedding'. " +
                        "Check that Ollama is running and the model is pulled: " + model);
            }

            @SuppressWarnings("unchecked")
            List<Number> v = (List<Number>) response.get("embedding");
            float[] out = new float[v.size()];
            for (int i = 0; i < v.size(); i++) out[i] = v.get(i).floatValue();
            return out;
        } catch (WebClientResponseException.NotFound nf) {
            throw new IllegalStateException("Ollama returned 404 for embeddings. Ensure the embedding model '" + model + "' is pulled and that " + baseUrl + "/api/embeddings is reachable.", nf);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to fetch embedding after " + attempts + " attempts", ex);
        }
    }

    /**
     * Backfill embeddings for movies without a vector.
     * Processes records in bounded batches to avoid loading the entire catalog.
     */
    public void backfillEmbeddings() {
        while (true) {
            List<Movie> batch = movies.findTop500ByEmbeddingIsNullOrderByIdAsc();
            if (batch.isEmpty()) {
                log.info("Embedding backfill complete; all records processed.");
                return;
            }

            List<Movie> toSave = new ArrayList<>(batch.size());
            log.info("Embedding backfill processing {} movies", batch.size());
            for (Movie movie : batch) {
                String text = buildEmbeddingText(movie);
                float[] vector = embed(text);
                movie.setEmbedding(vector);
                movie.setEmbeddingModel(model);
                movie.setEmbeddingUpdatedAt(OffsetDateTime.now());
                toSave.add(movie);
            }

            persistBatch(toSave);
        }
    }

    private String buildEmbeddingText(Movie movie) {
        StringBuilder sb = new StringBuilder();
        String title = firstNonBlank(movie.getPrimaryTitle(), movie.getOriginalTitle(), movie.getTconst());
        sb.append(title);

        List<String> tags = buildTags(movie);
        if (!tags.isEmpty()) {
            sb.append("\n\nTags: ").append(String.join(", ", tags));
        }

        return sb.toString();
    }

    @Transactional
    void persistBatch(List<Movie> batch) {
        movies.saveAll(batch);
        movies.flush();
    }

    private List<String> buildTags(Movie movie) {
        List<String> tags = new ArrayList<>();
        if (movie.getTitleType() != null && !movie.getTitleType().isBlank()) {
            tags.add("type:" + movie.getTitleType());
        }
        if (movie.getGenres() != null) {
            Arrays.stream(movie.getGenres())
                    .filter(g -> g != null && !g.isBlank())
                    .map(g -> "genre:" + g)
                    .forEach(tags::add);
        }
        if (movie.getStartYear() != null) {
            tags.add("year:" + movie.getStartYear());
        }
        if (movie.getRuntimeMinutes() != null && movie.getRuntimeMinutes() > 0) {
            tags.add("runtime:" + movie.getRuntimeMinutes() + "m");
        }
        if (movie.getRating() != null) {
            tags.add("rating:" + String.format(Locale.US, "%.1f", movie.getRating()));
        }
        if (movie.getVotes() != null && movie.getVotes() > 0) {
            tags.add("votes:" + movie.getVotes());
        }
        return tags;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException response) {
            return response.getStatusCode().is5xxServerError();
        }
        return throwable instanceof WebClientRequestException || throwable instanceof TimeoutException;
    }
}
