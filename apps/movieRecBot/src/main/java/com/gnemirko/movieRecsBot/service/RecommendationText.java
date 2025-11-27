package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.dto.RecResponse;

public final class RecommendationText {
    private RecommendationText(){}

    public static String format(RecResponse r) {
        if (r == null || r.movies() == null || r.movies().isEmpty()) {
            return "Пока нет релевантных фильмов 😕";
        }
        StringBuilder sb = new StringBuilder("🎬 *Рекомендации:*\n");
        r.movies().forEach(m -> sb.append("• ")
                .append(m.title() == null ? "‒" : m.title())
                .append(m.genres() == null || m.genres().isEmpty() ? "" : " (" + String.join("/", m.genres()) + ")")
                .append("\n"));
        return sb.toString();
    }
}