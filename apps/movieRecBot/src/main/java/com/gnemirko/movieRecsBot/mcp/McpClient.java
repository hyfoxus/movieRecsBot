package com.gnemirko.movieRecsBot.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

@Component
@Slf4j
public class McpClient {

    private static final int MAX_TIMEOUT_RETRIES = 2;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;

    public McpClient(@Value("${app.mcp.base-url:http://imdb-mcp:8082}") String baseUrl,
                     WebClient.Builder builder,
                     ObjectMapper objectMapper,
                     @Value("${app.mcp.timeout-ms:4000}") long timeoutMs) {
        this.webClient = builder
                .baseUrl(baseUrl)
                .build();
        this.objectMapper = objectMapper;
        this.timeout = Duration.ofMillis(Math.max(timeoutMs, 1000));
    }

    public List<MovieContextItem> search(McpSearchRequest request) {
        if (request == null) {
            return List.of();
        }
        int totalAttempts = MAX_TIMEOUT_RETRIES + 1;
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                return executeSearch(request);
            } catch (Exception ex) {
                boolean timeoutFailure = isTimeoutException(ex);
                log.warn("MCP search attempt {}/{} for '{}' {}: {}", attempt, totalAttempts,
                        request.query(),
                        timeoutFailure ? "timed out" : "failed",
                        ex.getMessage());
                if (!timeoutFailure || attempt == totalAttempts) {
                    break;
                }
            }
        }
        return Collections.emptyList();
    }

    public Optional<MovieContextItem> lookupExact(McpLookupRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        McpToolRequest toolRequest = new McpToolRequest("movie.lookup", request.toArguments());
        try {
            McpToolResponse response = webClient.post()
                    .uri("/mcp/v1/tools")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(toolRequest)
                    .retrieve()
                    .bodyToMono(McpToolResponse.class)
                    .timeout(timeout)
                    .block(timeout);
            if (response == null || response.content() == null) {
                return Optional.empty();
            }
            return response.content().stream()
                    .filter(block -> "json".equalsIgnoreCase(block.type()))
                    .map(McpToolResponse.ContentBlock::json)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .map(node -> objectMapper.convertValue(node, MovieContextItem.class));
        } catch (Exception ex) {
            log.warn("MCP lookup for '{}' failed: {}", request.title(), ex.getMessage());
            return Optional.empty();
        }
    }

    private List<MovieContextItem> executeSearch(McpSearchRequest request) {
        McpToolRequest body = new McpToolRequest("movie.search", request.toArguments());
        McpToolResponse response = webClient.post()
                .uri("/mcp/v1/tools")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(McpToolResponse.class)
                .timeout(timeout)
                .block(timeout);

        if (response == null || response.content() == null) {
            return Collections.emptyList();
        }

        JsonNode jsonNode = response.content().stream()
                .filter(block -> "json".equalsIgnoreCase(block.type()))
                .map(McpToolResponse.ContentBlock::json)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (jsonNode == null) {
            return Collections.emptyList();
        }
        return objectMapper.convertValue(
                jsonNode,
                new TypeReference<List<MovieContextItem>>() {}
        );
    }

    private boolean isTimeoutException(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        if (throwable instanceof TimeoutException) {
            return true;
        }
        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable && isTimeoutException(cause)) {
            return true;
        }
        String message = throwable.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("timeout");
    }
}
