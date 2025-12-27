package com.gnemirko.mcpmovie.model;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record MovieSearchRequest(
        @NotBlank(message = "query is required") String query,
        Integer limit,
        Integer fromYear,
        Integer toYear,
        Integer runtimeMinutes,
        Double minRating,
        List<String> includeGenres,
        List<String> excludeGenres,
        List<String> actors
) {

    public MovieSearchRequest {
        query = query == null ? "" : query.trim();
        includeGenres = sanitize(includeGenres);
        excludeGenres = sanitize(excludeGenres);
        actors = sanitizeLower(actors);
    }

    private static List<String> sanitize(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static List<String> sanitizeLower(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return source.stream()
                .filter(Objects::nonNull)
                .map(v -> v.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
