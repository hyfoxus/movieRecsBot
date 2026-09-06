package com.gnemirko.movieRecsBot.service.recommendation;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the last recommendation batch shown to each chat so "more like these" pagination and
 * per-movie 👍/👎 feedback can reference it by position, without needing a stable movie id or a DB table.
 * In-memory only, same idiom as {@code ReportSessionStore}/{@code MenuStateService} elsewhere in this package.
 */
@Component
public class RecommendationSessionStore {

    private static final int MAX_SHOWN_KEYS = 20;

    public record Session(String lastPromptText, List<RecommendationMovie> lastMovies, Set<String> shownKeys) {
    }

    private final ConcurrentHashMap<Long, Session> sessions = new ConcurrentHashMap<>();

    public void record(long chatId, String promptText, List<RecommendationMovie> movies) {
        if (movies == null) {
            movies = List.of();
        }
        Session previous = sessions.get(chatId);
        LinkedHashSet<String> keys = previous == null ? new LinkedHashSet<>() : new LinkedHashSet<>(previous.shownKeys());
        for (RecommendationMovie movie : movies) {
            keys.add(key(movie));
        }
        while (keys.size() > MAX_SHOWN_KEYS) {
            keys.remove(keys.iterator().next());
        }
        sessions.put(chatId, new Session(promptText, List.copyOf(movies), Set.copyOf(keys)));
    }

    public Optional<Session> get(long chatId) {
        return Optional.ofNullable(sessions.get(chatId));
    }

    public Set<String> excludedTitleKeys(long chatId) {
        Session session = sessions.get(chatId);
        return session == null ? Set.of() : session.shownKeys();
    }

    public void clear(long chatId) {
        sessions.remove(chatId);
    }

    public static String key(RecommendationMovie movie) {
        if (movie == null) {
            return "";
        }
        String title = movie.getTitle() == null ? "" : movie.getTitle().trim().toLowerCase(Locale.ROOT);
        String year = movie.getYear() == null ? "" : String.valueOf(movie.getYear());
        return title + "|" + year;
    }
}
