package com.gnemirko.movieRecsBot.mcp;

import java.util.Map;

public record McpToolRequest(String name, Map<String, Object> arguments) {
}
