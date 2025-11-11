package com.gnemirko.movieRecsBot.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MovieContextItem(
        String tconst,
        String title,
        Integer year,
        Double rating,
        Integer votes,
        double similarity,
        List<String> genres,
        Map<String, Object> metadata
) {
}
