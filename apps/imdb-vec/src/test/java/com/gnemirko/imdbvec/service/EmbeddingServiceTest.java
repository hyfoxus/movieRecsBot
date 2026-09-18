package com.gnemirko.imdbvec.service;

import com.gnemirko.imdbvec.model.Movie;
import com.gnemirko.imdbvec.repo.MovieRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    private MovieRepository movieRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private EmbeddingService service(int maxRetries, Duration retryDelay) {
        return new EmbeddingService(baseUrl, "nomic-embed-text", maxRetries, retryDelay, movieRepository, jdbcTemplate);
    }

    private void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    @Test
    void embedReturnsVectorFromOllamaResponse() throws IOException {
        server.createContext("/api/embeddings", exchange ->
                respondJson(exchange, 200, "{\"embedding\":[0.1,0.2,0.3]}"));

        float[] result = service(1, Duration.ZERO).embed("hello world");

        assertThat(result).containsExactly(0.1f, 0.2f, 0.3f);
    }

    @Test
    void embedSendsConfiguredModelAndPromptText() throws IOException {
        AtomicReference<String> captured = new AtomicReference<>();
        server.createContext("/api/embeddings", exchange -> {
            captured.set(readBody(exchange));
            respondJson(exchange, 200, "{\"embedding\":[1.0]}");
        });

        service(1, Duration.ZERO).embed("The Matrix");

        assertThat(captured.get()).contains("\"model\":\"nomic-embed-text\"");
        assertThat(captured.get()).contains("\"prompt\":\"The Matrix\"");
    }

    @Test
    void embedThrowsDescriptiveErrorOn404() throws IOException {
        server.createContext("/api/embeddings", exchange -> respondJson(exchange, 404, "{}"));

        assertThatThrownBy(() -> service(1, Duration.ZERO).embed("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("404");
    }

    @Test
    void embedThrowsWhenResponseHasNoEmbeddingKey() throws IOException {
        server.createContext("/api/embeddings", exchange -> respondJson(exchange, 200, "{\"other\":1}"));

        assertThatThrownBy(() -> service(1, Duration.ZERO).embed("x"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void embedRetriesOnServerErrorThenSucceeds() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/api/embeddings", exchange -> {
            if (calls.getAndIncrement() == 0) {
                respondJson(exchange, 503, "{}");
            } else {
                respondJson(exchange, 200, "{\"embedding\":[9.0]}");
            }
        });

        float[] result = service(3, Duration.ofMillis(10)).embed("retry me");

        assertThat(result).containsExactly(9.0f);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void embedThrowsAfterRetriesExhausted() throws IOException {
        server.createContext("/api/embeddings", exchange -> respondJson(exchange, 500, "{}"));

        assertThatThrownBy(() -> service(2, Duration.ofMillis(5)).embed("always failing"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void backfillEmbeddingsStopsImmediatelyWhenNoRecordsNeedEmbedding() {
        when(movieRepository.findTop500ByEmbeddingModelIsNullOrderByIdAsc()).thenReturn(List.of());

        service(1, Duration.ZERO).backfillEmbeddings();

        verify(movieRepository, times(1)).findTop500ByEmbeddingModelIsNullOrderByIdAsc();
        verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    void backfillEmbeddingsProcessesBatchesUntilRepositoryIsExhausted() throws IOException {
        server.createContext("/api/embeddings", exchange -> respondJson(exchange, 200, "{\"embedding\":[1.0,2.0]}"));

        Movie first = new Movie();
        first.setId(1L);
        first.setTconst("tt0000001");
        first.setPrimaryTitle("First Movie");

        Movie second = new Movie();
        second.setId(2L);
        second.setTconst("tt0000002");
        second.setPrimaryTitle("Second Movie");

        when(movieRepository.findTop500ByEmbeddingModelIsNullOrderByIdAsc())
                .thenReturn(List.of(first))
                .thenReturn(List.of(second))
                .thenReturn(List.of());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Long.class))).thenReturn(List.of());

        service(1, Duration.ZERO).backfillEmbeddings();

        verify(movieRepository, times(3)).findTop500ByEmbeddingModelIsNullOrderByIdAsc();
        verify(jdbcTemplate, times(2)).batchUpdate(anyString(), any(List.class), anyInt(), any());
        assertThat(first.getEmbedding()).containsExactly(1.0f, 2.0f);
        assertThat(first.getEmbeddingModel()).isEqualTo("nomic-embed-text");
        assertThat(second.getEmbedding()).containsExactly(1.0f, 2.0f);
    }

    @Test
    void backfillEmbeddingsBuildsPromptFromTitleGenresRuntimeRatingActorsAndPlot() throws IOException {
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        server.createContext("/api/embeddings", exchange -> {
            capturedPrompt.set(readBody(exchange));
            respondJson(exchange, 200, "{\"embedding\":[1.0]}");
        });

        Movie movie = new Movie();
        movie.setId(42L);
        movie.setTconst("tt0000042");
        movie.setPrimaryTitle("Sample Title");
        movie.setStartYear((short) 1999);
        movie.setGenres(new String[]{"Drama", "Drama", "Comedy"});
        movie.setRuntimeMinutes((short) 120);
        movie.setIsAdult(false);
        movie.setRating(8.456);
        movie.setPlot("A".repeat(900));

        when(movieRepository.findTop500ByEmbeddingModelIsNullOrderByIdAsc())
                .thenReturn(List.of(movie))
                .thenReturn(List.of());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(42L)))
                .thenReturn(List.of("Actor One", "Actor Two"));

        service(1, Duration.ZERO).backfillEmbeddings();

        String prompt = capturedPrompt.get();
        // describeYears() renders "since <year>" whenever only startYear is set (no endYear) —
        // which is the normal shape for a plain movie row, since endYear is a TV-series concept.
        assertThat(prompt).contains("Sample Title (since 1999)");
        assertThat(prompt).contains("Genres: Drama, Comedy");
        assertThat(prompt).contains("Runtime: 120 minutes");
        assertThat(prompt).contains("Audience: All ages");
        assertThat(prompt).contains("Average rating: 8.5/10");
        assertThat(prompt).contains("Actors: Actor One, Actor Two");
        assertThat(prompt).contains("A".repeat(797) + "...");
        assertThat(prompt).doesNotContain("A".repeat(798));
    }

    @Test
    void persistBatchWritesVectorLiteralAndNullsForMissingEmbedding() {
        Movie withEmbedding = new Movie();
        withEmbedding.setId(1L);
        withEmbedding.setEmbedding(new float[]{1.5f, 2.5f});
        withEmbedding.setEmbeddingModel("nomic-embed-text");

        Movie withoutEmbedding = new Movie();
        withoutEmbedding.setId(2L);
        withoutEmbedding.setEmbedding(null);

        service(1, Duration.ZERO).persistBatch(List.of(withEmbedding, withoutEmbedding));

        ArgumentCaptor<List> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate).batchUpdate(anyString(), batchCaptor.capture(), eq(100), any());
        assertThat(batchCaptor.getValue()).containsExactly(withEmbedding, withoutEmbedding);
    }
}
