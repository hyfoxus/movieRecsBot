package com.gnemirko.movieRecsBot.service.recommendation;

import com.gnemirko.movieRecsBot.service.TitleFormatter;
import org.springframework.stereotype.Component;

import static com.gnemirko.movieRecsBot.service.TelegramMessageFormatter.escapeHtml;

@Component
public class RecommendationRenderer {

    public String render(RecommendationResponseParser.ParsedResponse parsed) {
        StringBuilder sb = new StringBuilder();
        if (!isBlank(parsed.intro())) {
            sb.append("<b>").append(escapeHtml(stripMarkdown(parsed.intro()))).append("</b>\n\n");
        }
        int idx = 1;
        for (RecommendationMovie movie : parsed.movies()) {
            if (idx > 5) break;
            sb.append(idx)
                    .append(". <code>")
                    .append(escapeHtml(TitleFormatter.formatWithVerifiedYear(stripMarkdown(nvl(movie.getTitle())), movie.getYear())))
                    .append("</code> — ")
                    .append(escapeHtml(stripMarkdown(nvl(movie.getReason()))))
                    .append("\n\n");
            idx++;
        }
        return sb.toString().trim();
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }

    private static String stripMarkdown(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.replaceAll("[\\*_`~]+", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
