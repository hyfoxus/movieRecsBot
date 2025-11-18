package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.mcp.MovieContextItem;
import com.gnemirko.movieRecsBot.mcp.MovieContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
class CatalogMetadataResolver {

    private final MovieContextService movieContextService;

    void reconcileYears(List<RecommendationService.Movie> movies,
                        List<MovieContextItem> catalogItems) {
        if (movies == null || movies.isEmpty()) {
            return;
        }
        Map<String, MovieContextItem> index = indexCatalog(catalogItems);
        Map<String, Optional<MovieContextItem>> lookupCache = new HashMap<>();

        for (RecommendationService.Movie movie : movies) {
            if (movie == null || isBlank(movie.title)) {
                continue;
            }
            MovieContextItem match = matchFromIndex(movie.title, index);
            if (match == null) {
                match = lookupByTitle(movie.title, lookupCache);
            }
            if (match == null || match.year() == null) {
                dropUnverifiedYear(movie);
                continue;
            }
            Integer previous = movie.year;
            movie.year = match.year();
            if (!match.year().equals(previous)) {
                log.debug("Corrected '{}' year from {} to {} using MCP catalog.", movie.title, previous, match.year());
            }
        }
    }

    private void dropUnverifiedYear(RecommendationService.Movie movie) {
        if (movie.year != null) {
            log.debug("Dropping unverified release year for '{}'.", movie.title);
        }
        movie.year = null;
    }

    private MovieContextItem lookupByTitle(String title,
                                           Map<String, Optional<MovieContextItem>> cache) {
        String sanitized = TitleFormatter.stripTrailingYear(title);
        String key = normalizeTitle(sanitized);
        if (key.isEmpty()) {
            return null;
        }
        Optional<MovieContextItem> cached = cache.get(key);
        if (cached != null) {
            return cached.orElse(null);
        }
        String query = sanitized.isEmpty() ? nvl(title) : sanitized;
        Optional<MovieContextItem> fetched = movieContextService.lookupByTitle(query);
        cache.put(key, fetched);
        return fetched.orElse(null);
    }

    private Map<String, MovieContextItem> indexCatalog(List<MovieContextItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, MovieContextItem> index = new HashMap<>();
        for (MovieContextItem item : items) {
            if (item == null || isBlank(item.title())) {
                continue;
            }
            String key = normalizeTitle(item.title());
            if (key.isEmpty() || index.containsKey(key)) {
                continue;
            }
            index.put(key, item);
        }
        return index;
    }

    private MovieContextItem matchFromIndex(String title,
                                            Map<String, MovieContextItem> index) {
        if (index.isEmpty() || isBlank(title)) {
            return null;
        }
        return index.get(normalizeTitle(title));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String normalizeTitle(String value) {
        String stripped = TitleFormatter.stripTrailingYear(value);
        if (stripped == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        String lower = stripped.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
