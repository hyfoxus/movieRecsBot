package com.gnemirko.movieRecsBot.mcp;

import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.service.UserLanguage;
import com.gnemirko.movieRecsBot.service.recommendation.UserIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MovieContextService {

    private final McpClient mcpClient;

    public ContextBlock buildContextBlock(String userQuery,
                                          String profileSummary,
                                          UserProfile profile,
                                          UserLanguage language,
                                          UserIntent intent,
                                          List<String> actorFilters) {
        if (profile == null) {
            return ContextBlock.empty();
        }
        String query = buildQuery(userQuery, profileSummary, intent);
        List<String> includeGenres = mergeIncludeGenres(profile, intent);
        List<String> excludeGenres = mergeExcludeGenres(profile, intent);
        List<String> sanitizedActorFilters = sanitizeList(actorFilters);

        if (query.isBlank()) {
            log.debug("Skipping MCP search: empty query after sanitization");
            return ContextBlock.empty();
        }

        Integer fromYear = intent == null ? null : intent.releaseYearFrom();
        List<MovieContextItem> items;
        try {
            items = mcpClient.search(new McpSearchRequest(
                    query,
                    includeGenres,
                    excludeGenres,
                    sanitizedActorFilters,
                    fromYear,
                    5));
        } catch (IllegalArgumentException ex) {
            log.warn("Failed to build MCP search request: {}", ex.getMessage());
            return ContextBlock.empty();
        }
        if (items.isEmpty()) {
            return ContextBlock.empty();
        }

        StringBuilder block = new StringBuilder();
        String header = contextHeader(language);
        if (!header.isBlank()) {
            block.append(header).append("\n");
        }
        int idx = 1;
        for (MovieContextItem item : items) {
            block.append(formatCatalogEntry(idx++, item)).append("\n");
        }
        return new ContextBlock(block.toString().trim(), items);
    }

    public Optional<MovieContextItem> lookupByTitle(String title, Integer year) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        try {
            Optional<MovieContextItem> exact = mcpClient.lookupExact(new McpLookupRequest(title, year));
            if (exact.isPresent()) {
                return exact;
            }
            List<MovieContextItem> matches = mcpClient.search(new McpSearchRequest(
                    buildLookupQuery(title, year),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    year,
                    5));
            if (matches.isEmpty()) {
                return Optional.empty();
            }
            if (year != null) {
                return matches.stream()
                        .filter(item -> item.year() != null && item.year().equals(year))
                        .findFirst()
                        .or(() -> matches.stream().findFirst());
            }
            return Optional.ofNullable(matches.get(0));
        } catch (Exception ex) {
            log.warn("Failed to look up movie '{}' via MCP: {}", title, ex.getMessage());
            return Optional.empty();
        }
    }

    private String buildLookupQuery(String title, Integer year) {
        if (year == null) {
            return title.trim();
        }
        return (title + " " + year).trim();
    }

    private String buildQuery(String userQuery, String profileSummary, UserIntent intent) {
        List<String> segments = new ArrayList<>();

        String rewritten = intent == null ? null : intent.rewrittenQuery();
        String base = firstNonBlank(rewritten, userQuery);
        if (!base.isBlank()) {
            segments.add(base);
        }

        if (intent != null && intent.descriptors() != null && !intent.descriptors().isEmpty()) {
            segments.add("Vibe: " + String.join(", ", intent.descriptors()));
        }
        if (intent != null && intent.runtimeMinutes() != null) {
            segments.add("runtime <= " + intent.runtimeMinutes() + " minutes");
        }
        if (profileSummary != null && !profileSummary.isBlank()) {
            segments.add(profileSummary.trim());
        }

        return String.join(" | ", segments).trim();
    }

    private List<String> mergeIncludeGenres(UserProfile profile, UserIntent intent) {
        LinkedHashSet<String> include = new LinkedHashSet<>();
        if (intent != null) {
            include.addAll(sanitizeList(intent.includeGenres()));
        }
        if (profile != null) {
            include.addAll(sanitizeCollection(profile.getLikedGenres()));
        }
        return List.copyOf(include);
    }

    private List<String> mergeExcludeGenres(UserProfile profile, UserIntent intent) {
        LinkedHashSet<String> exclude = new LinkedHashSet<>();
        if (intent != null) {
            exclude.addAll(sanitizeList(intent.excludeGenres()));
        }
        if (profile != null && profile.getBlocked() != null) {
            profile.getBlocked().stream()
                    .map(tag -> tag.replace("genre:", ""))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(exclude::add);
        }
        return List.copyOf(exclude);
    }

    private List<String> sanitizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(sanitizeCollection(values));
    }

    private LinkedHashSet<String> sanitizeCollection(Iterable<String> values) {
        LinkedHashSet<String> sanitized = new LinkedHashSet<>();
        if (values == null) {
            return sanitized;
        }
        for (String value : values) {
            String trimmed = safeTrim(value);
            if (!trimmed.isEmpty()) {
                sanitized.add(trimmed);
            }
        }
        return sanitized;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String contextHeader(UserLanguage language) {
        return "CATALOG FACTS (copy exact title/year from this list; never invent years):";
    }

    static String formatCatalogEntry(int index, MovieContextItem item) {
        StringBuilder entry = new StringBuilder();
        entry.append(index).append(") Title: ").append(nvl(item.title(), item.tconst()));
        entry.append(" | Year: ").append(item.year() == null ? "unknown" : item.year());
        if (item.rating() != null) {
            entry.append(" | Rating: ").append(String.format(Locale.US, "%.1f", item.rating()));
        }
        if (item.genres() != null && !item.genres().isEmpty()) {
            entry.append(" | Genres: ").append(String.join(", ", item.genres()));
        }
        if (item.actors() != null && !item.actors().isEmpty()) {
            String actors = item.actors().stream()
                    .map(person -> person == null ? "" : person.name())
                    .filter(name -> name != null && !name.isBlank())
                    .limit(5)
                    .collect(Collectors.joining(", "));
            if (!actors.isBlank()) {
                entry.append(" | Actors: ").append(actors);
            }
        }
        Object plot = item.metadata() == null ? null : item.metadata().get("plot");
        if (plot instanceof String plotText && !plotText.isBlank()) {
            entry.append(" | Plot: ").append(plotText.trim());
        }
        entry.append(" | Similarity: ").append(formatSimilarity(item.similarity()));
        entry.append(" | IMDb ID: ").append(item.tconst() == null ? "unknown" : item.tconst());
        return entry.toString();
    }

    private static String formatSimilarity(double similarity) {
        if (similarity <= 0) {
            return "0.00";
        }
        double capped = Math.min(similarity, 0.999);
        return String.format(Locale.US, "%.2f", capped);
    }

    private static String nvl(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback == null ? "" : fallback;
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    public record ContextBlock(String block, List<MovieContextItem> items) {
        public static ContextBlock empty() {
            return new ContextBlock("", List.of());
        }
    }
}
