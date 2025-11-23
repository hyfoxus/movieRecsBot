package com.gnemirko.movieRecsBot.mcp;

import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.service.UserLanguage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
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
                                          List<String> actorFilters) {
        if (profile == null) {
            return ContextBlock.empty();
        }
        String query = userQuery == null ? "" : userQuery.trim();
        if (profileSummary != null && !profileSummary.isBlank()) {
            query = (query.isBlank() ? "" : query + " | ") + profileSummary;
        }

        List<String> includeGenres = new ArrayList<>(profile.getLikedGenres());
        List<String> excludeGenres = profile.getBlocked()
                .stream()
                .map(tag -> tag.replace("genre:", ""))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        List<MovieContextItem> items = mcpClient.search(query, includeGenres, excludeGenres, actorFilters, 5);
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

    public Optional<MovieContextItem> lookupByTitle(String title) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        try {
            List<MovieContextItem> matches = mcpClient.search(title.trim(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), 1);
            if (matches.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(matches.get(0));
        } catch (Exception ex) {
            log.warn("Failed to look up movie '{}' via MCP: {}", title, ex.getMessage());
            return Optional.empty();
        }
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

    public record ContextBlock(String block, List<MovieContextItem> items) {
        public static ContextBlock empty() {
            return new ContextBlock("", List.of());
        }
    }
}
