package com.gnemirko.mcpmovie.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MovieMcpPropertiesTest {

    @Test
    void blankOrNullTextFieldsFallBackToDefaults() {
        MovieMcpProperties properties = new MovieMcpProperties(null, "  ", "", 15, 300);

        assertThat(properties.name()).isEqualTo("Movie Recommendations MCP");
        assertThat(properties.version()).isEqualTo("1.0.0");
        assertThat(properties.description()).isEqualTo("Provides pgvector-backed movie search and metadata.");
    }

    @Test
    void nonPositiveNumbersFallBackToDefaults() {
        MovieMcpProperties properties = new MovieMcpProperties("n", "v", "d", 0, -5);

        assertThat(properties.maxResults()).isEqualTo(15);
        assertThat(properties.cacheTtlSeconds()).isEqualTo(300);
    }

    @Test
    void validValuesArePreservedAsIs() {
        MovieMcpProperties properties = new MovieMcpProperties("Custom", "9.9.9", "Custom desc", 42, 120);

        assertThat(properties.name()).isEqualTo("Custom");
        assertThat(properties.version()).isEqualTo("9.9.9");
        assertThat(properties.description()).isEqualTo("Custom desc");
        assertThat(properties.maxResults()).isEqualTo(42);
        assertThat(properties.cacheTtlSeconds()).isEqualTo(120);
    }
}
