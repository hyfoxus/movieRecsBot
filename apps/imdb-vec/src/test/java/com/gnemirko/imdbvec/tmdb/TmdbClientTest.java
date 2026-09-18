package com.gnemirko.imdbvec.tmdb;

import com.gnemirko.imdbvec.config.TmdbProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TmdbClientTest {

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

    private TmdbProperties properties() {
        TmdbProperties properties = new TmdbProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(baseUrl);
        properties.setApiKey("test-key");
        properties.setLanguage("en-US");
        properties.setMaxRetries(2);
        properties.setRetryDelay(Duration.ofMillis(10));
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(2));
        return properties;
    }

    private void respondJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void fetchOverviewReturnsFirstOverviewFromMovieResults() throws IOException {
        server.createContext("/find/tt1234567", exchange ->
                respondJson(exchange, 200, """
                        {"movie_results":[{"id":1,"overview":"A great movie."},{"id":2,"overview":"Another one."}]}
                        """));

        TmdbClient client = new TmdbClient(properties());

        Optional<String> overview = client.fetchOverview("tt1234567");

        assertThat(overview).contains("A great movie.");
    }

    @Test
    void fetchOverviewSkipsResultsWithBlankOverview() throws IOException {
        server.createContext("/find/tt7654321", exchange ->
                respondJson(exchange, 200, """
                        {"movie_results":[{"id":1,"overview":"   "},{"id":2,"overview":"Real overview"}]}
                        """));

        Optional<String> overview = new TmdbClient(properties()).fetchOverview("tt7654321");

        assertThat(overview).contains("Real overview");
    }

    @Test
    void fetchOverviewReturnsEmptyWhenNoMovieResults() throws IOException {
        server.createContext("/find/tt0000000", exchange ->
                respondJson(exchange, 200, "{\"movie_results\":[]}"));

        Optional<String> overview = new TmdbClient(properties()).fetchOverview("tt0000000");

        assertThat(overview).isEmpty();
    }

    @Test
    void fetchOverviewReturnsEmptyOn404() throws IOException {
        server.createContext("/find/ttmissing", exchange -> respondJson(exchange, 404, "{}"));

        Optional<String> overview = new TmdbClient(properties()).fetchOverview("ttmissing");

        assertThat(overview).isEmpty();
    }

    @Test
    void fetchOverviewReturnsEmptyOn401WithoutThrowing() throws IOException {
        server.createContext("/find/ttunauth", exchange -> respondJson(exchange, 401, "{}"));

        Optional<String> overview = new TmdbClient(properties()).fetchOverview("ttunauth");

        assertThat(overview).isEmpty();
    }

    @Test
    void fetchOverviewRetriesOnServerErrorThenSucceeds() throws IOException {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/find/ttretry", exchange -> {
            if (calls.getAndIncrement() == 0) {
                respondJson(exchange, 503, "{}");
            } else {
                respondJson(exchange, 200, "{\"movie_results\":[{\"id\":1,\"overview\":\"Recovered\"}]}");
            }
        });

        Optional<String> overview = new TmdbClient(properties()).fetchOverview("ttretry");

        assertThat(overview).contains("Recovered");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void fetchOverviewThrowsWhenServerErrorsPersistAfterRetriesExhausted() throws IOException {
        server.createContext("/find/ttdown", exchange -> respondJson(exchange, 500, "{}"));

        assertThatThrownBy(() -> new TmdbClient(properties()).fetchOverview("ttdown"))
                .isInstanceOf(WebClientResponseException.class);
    }

    @Test
    void fetchOverviewReturnsEmptyWhenDisabled() {
        TmdbProperties properties = properties();
        properties.setEnabled(false);

        Optional<String> overview = new TmdbClient(properties).fetchOverview("tt1234567");

        assertThat(overview).isEmpty();
    }

    @Test
    void isEnabledRequiresBothFlagAndApiKey() {
        TmdbProperties enabledNoKey = properties();
        enabledNoKey.setApiKey("");
        assertThat(new TmdbClient(enabledNoKey).isEnabled()).isFalse();

        TmdbProperties disabledWithKey = properties();
        disabledWithKey.setEnabled(false);
        assertThat(new TmdbClient(disabledWithKey).isEnabled()).isFalse();

        assertThat(new TmdbClient(properties()).isEnabled()).isTrue();
    }

    @Test
    void fetchOverviewReturnsEmptyForBlankImdbId() {
        Optional<String> overview = new TmdbClient(properties()).fetchOverview("   ");
        assertThat(overview).isEmpty();
    }
}
