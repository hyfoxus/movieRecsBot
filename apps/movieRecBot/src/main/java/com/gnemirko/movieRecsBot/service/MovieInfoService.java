package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.mcp.MovieContextItem;
import com.gnemirko.movieRecsBot.mcp.MovieContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;

import static com.gnemirko.movieRecsBot.service.TelegramMessageFormatter.escapeHtml;

@Service
@RequiredArgsConstructor
public class MovieInfoService {

    private static final NumberFormat VOTE_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private final MovieContextService movieContextService;

    public String describeMovie(String requestedTitle) {
        if (requestedTitle == null || requestedTitle.isBlank()) {
            return "Мне нужна конкретная картина: назови, пожалуйста, фильм.";
        }
        Optional<MovieContextItem> match = movieContextService.lookupByTitle(requestedTitle);
        return match
                .map(this::formatMovie)
                .orElseGet(() ->
                        "Не нашёл ничего похожего на \"" + escapeHtml(requestedTitle.trim()) + "\". " +
                                "Проверь написание или уточни, какой именно фильм нужен."
                );
    }

    private String formatMovie(MovieContextItem item) {
        StringBuilder builder = new StringBuilder();
        String title = firstNonBlank(item.title(), item.tconst());
        builder.append("<b>").append(escapeHtml(title));
        if (item.year() != null) {
            builder.append(" (").append(item.year()).append(")");
        }
        builder.append("</b>");

        if (item.rating() != null) {
            builder.append("\n⭐ ").append(String.format(Locale.US, "%.1f", item.rating()));
            if (item.votes() != null && item.votes() > 0) {
                builder.append(" • ").append(VOTE_FORMAT.format(item.votes())).append(" votes");
            }
        }
        if (item.genres() != null && !item.genres().isEmpty()) {
            builder.append("\nЖанры: ").append(escapeHtml(String.join(", ", item.genres())));
        }

        Object runtime = metadataValue(item, "runtimeMinutes");
        if (runtime instanceof Number number && number.intValue() > 0) {
            builder.append("\nХронометраж: ").append(number.intValue()).append(" мин.");
        }

        Object plot = metadataValue(item, "plot");
        if (plot instanceof String plotText && !plotText.isBlank()) {
            builder.append("\nСюжет: ").append(escapeHtml(plotText.trim()));
        }

        if (item.actors() != null && !item.actors().isEmpty()) {
            String cast = item.actors().stream()
                    .map(actor -> actor == null ? "" : actor.name())
                    .filter(name -> name != null && !name.isBlank())
                    .limit(5)
                    .map(name -> escapeHtml(name.trim()))
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            if (!cast.isBlank()) {
                builder.append("\nВ ролях: ").append(cast);
            }
        }

        builder.append("\nIMDb ID: ").append(escapeHtml(firstNonBlank(item.tconst(), "unknown")));
        return builder.toString();
    }

    private Object metadataValue(MovieContextItem item, String key) {
        if (item.metadata() == null || item.metadata().isEmpty()) {
            return null;
        }
        return item.metadata().get(key);
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }
}
