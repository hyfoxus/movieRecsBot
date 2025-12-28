package com.gnemirko.mcpmovie.model;

import java.util.List;
import java.util.Map;

public record MovieContext(
        String tconst,
        String title,
        Integer year,
        Double rating,
        Integer votes,
        double similarity,
        List<String> genres,
        List<MovieActor> actors,
        Map<String, Object> metadata
) {
}
