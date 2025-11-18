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
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
public class McpClient {

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

    public List<MovieContextItem> search(String query,
                                         List<String> includeGenres,
                                         List<String> excludeGenres,
                                         List<String> actorFilters,
                                         int limit) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("query", query);
        arguments.put("limit", limit);
        if (includeGenres != null && !includeGenres.isEmpty()) {
            arguments.put("includeGenres", includeGenres);
        }
        if (excludeGenres != null && !excludeGenres.isEmpty()) {
            arguments.put("excludeGenres", excludeGenres);
        }
        if (actorFilters != null && !actorFilters.isEmpty()) {
            arguments.put("actors", actorFilters);
        }

        McpToolRequest request = new McpToolRequest("movie.search", arguments);
        try {
            McpToolResponse response = webClient.post()
                    .uri("/mcp/v1/tools")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(McpToolResponse.class)
                    .timeout(timeout)
                    .onErrorResume(ex -> {
                        log.warn("MCP search call failed: {}", ex.getMessage());
                        return Mono.empty();
                    })
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
        } catch (Exception ex) {
            log.warn("Failed to fetch movie context from MCP: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }
}
