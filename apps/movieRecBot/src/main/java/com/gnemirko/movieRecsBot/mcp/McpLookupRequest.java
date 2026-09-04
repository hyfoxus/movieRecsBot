package com.gnemirko.movieRecsBot.mcp;

import java.util.HashMap;
import java.util.Map;

public record McpLookupRequest(String title, Integer year) {

    public McpLookupRequest {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        title = title.trim();
        if (year != null && year < 1850) {
            year = null;
        }
    }

    public Map<String, Object> toArguments() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("title", title);
        if (year != null) {
            arguments.put("year", year);
        }
        return arguments;
    }
}
