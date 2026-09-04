package com.gnemirko.movieRecsBot.mcp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record McpSearchRequest(String query,
                               List<String> includeGenres,
                               List<String> excludeGenres,
                               List<String> actors,
                               Integer fromYear,
                               int limit) {

    private static final int MAX_LIMIT = 20;
    private static final int MIN_LIMIT = 1;

    public McpSearchRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        query = query.trim();
        includeGenres = List.copyOf(Objects.requireNonNullElse(includeGenres, List.of()));
        excludeGenres = List.copyOf(Objects.requireNonNullElse(excludeGenres, List.of()));
        actors = List.copyOf(Objects.requireNonNullElse(actors, List.of()));
        if (fromYear != null && fromYear < 1850) {
            fromYear = 1850;
        }
        if (limit < MIN_LIMIT) {
            limit = MIN_LIMIT;
        } else if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
    }

    public Map<String, Object> toArguments() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("query", query);
        arguments.put("limit", limit);
        if (!includeGenres.isEmpty()) {
            arguments.put("includeGenres", includeGenres);
        }
        if (!excludeGenres.isEmpty()) {
            arguments.put("excludeGenres", excludeGenres);
        }
        if (!actors.isEmpty()) {
            arguments.put("actors", actors);
        }
        if (fromYear != null && fromYear > 0) {
            arguments.put("fromYear", fromYear);
        }
        return arguments;
    }
}
