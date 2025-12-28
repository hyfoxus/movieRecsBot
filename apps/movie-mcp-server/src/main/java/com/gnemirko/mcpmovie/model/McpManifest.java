package com.gnemirko.mcpmovie.model;

import java.util.List;

public record McpManifest(
        String name,
        String version,
        String description,
        List<McpTool> tools,
        List<McpResource> resources
) {
    public record McpTool(String name, String description, Object inputSchema) {
    }

    public record McpResource(String uri, String name, String description) {
    }
}
