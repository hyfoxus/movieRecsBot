package com.gnemirko.movieRecsBot.mcp;

import com.gnemirko.movieRecsBot.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MovieContextService {

    private final McpClient mcpClient;

    public String buildContextBlock(String userQuery, String profileSummary, UserProfile profile) {
        if (profile == null) {
            return "";
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

        List<MovieContextItem> items = mcpClient.search(query, includeGenres, excludeGenres, 5);
        if (items.isEmpty()) {
            return "";
        }

        StringBuilder block = new StringBuilder();
        block.append("Кандидаты из базы IMDb:\n");
        int idx = 1;
        for (MovieContextItem item : items) {
            block.append(idx++).append(". ")
                    .append(formatTitle(item))
                    .append(" | близость: ").append(formatSimilarity(item.similarity()));
            if (item.rating() != null) {
                block.append(" | рейтинг: ").append(String.format(Locale.US, "%.1f", item.rating()));
            }
            if (item.genres() != null && !item.genres().isEmpty()) {
                block.append(" | жанры: ").append(String.join(", ", item.genres()));
            }
            Object plot = item.metadata() == null ? null : item.metadata().get("plot");
            if (plot instanceof String plotText && !plotText.isBlank()) {
                block.append(" | сюжет: ").append(plotText.trim());
            }
            block.append("\n");
        }
        return block.toString().trim();
    }

    private String formatTitle(MovieContextItem item) {
        StringBuilder title = new StringBuilder();
        if (item.title() != null) {
            title.append(item.title());
        }
        if (item.year() != null) {
            title.append(" (").append(item.year()).append(")");
        }
        if (title.isEmpty() && item.tconst() != null) {
            title.append(item.tconst());
        }
        return title.toString();
    }

    private String formatSimilarity(double similarity) {
        if (similarity <= 0) {
            return "0.00";
        }
        double capped = Math.min(similarity, 0.999);
        return String.format(Locale.US, "%.2f", capped);
    }
}
