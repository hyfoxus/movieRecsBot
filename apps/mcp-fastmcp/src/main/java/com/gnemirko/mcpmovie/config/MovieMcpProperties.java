package com.gnemirko.mcpmovie.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mcp")
public record MovieMcpProperties(
        String name,
        String version,
        String description,
        int maxResults
) {

    public MovieMcpProperties {
        String defaultName = "Movie Recommendations MCP";
        String defaultVersion = "1.0.0";
        String defaultDescription = "Provides pgvector-backed movie search and metadata.";
        name = (name == null || name.isBlank()) ? defaultName : name;
        version = (version == null || version.isBlank()) ? defaultVersion : version;
        description = (description == null || description.isBlank()) ? defaultDescription : description;
        maxResults = maxResults <= 0 ? 15 : maxResults;
    }
}
