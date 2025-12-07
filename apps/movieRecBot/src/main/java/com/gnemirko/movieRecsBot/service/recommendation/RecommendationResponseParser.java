package com.gnemirko.movieRecsBot.service.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.service.UserLanguage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.gnemirko.movieRecsBot.service.TelegramMessageFormatter.stripCodeFence;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendationResponseParser {

    private final ObjectMapper objectMapper;
    public ParsedResponse parse(String rawJson,
                                UserProfile profile,
                                String userText,
                                UserLanguage expectedLanguage) {
        String json = normalizeJsonPayload(rawJson);
        ParsedResponse result = new ParsedResponse();
        try {
            JsonNode root = objectMapper.readTree(json);
            result.intro = text(root.get("intro"));
            result.languageIso = text(root.get("language"));
            JsonNode arr = root.get("movies");
            if (arr != null && arr.isArray()) {
                List<RecommendationMovie> parsedMovies = new ArrayList<>();
                for (JsonNode node : arr) {
                    RecommendationMovie movie = new RecommendationMovie();
                    movie.setTitle(text(node.get("title")));
                    movie.setReason(text(node.get("reason")));
                    movie.setYear(node.hasNonNull("year") ? node.get("year").asInt() : null);

                    Set<String> genres = new HashSet<>();
                    JsonNode genreNodes = node.get("genres");
                    if (genreNodes != null && genreNodes.isArray()) {
                        for (JsonNode g : genreNodes) {
                            if (g.isTextual()) {
                                genres.add(g.asText());
                            }
                        }
                    }
                    movie.setGenres(genres);
                    if (!isBlank(movie.getTitle())) {
                        parsedMovies.add(movie);
                    }
                }
                List<RecommendationMovie> filtered = postFilter(parsedMovies, profile, userText);
                result.movies = filtered;
            }
            result.reminder = text(root.get("reminder"));
            warnIfLanguageMismatch(expectedLanguage, result.languageIso);
        } catch (Exception ex) {
            log.debug("Failed to parse LLM JSON payload: {}", ex.getMessage());
            result.movies = List.of();
        }
        if (result.movies == null) {
            result.movies = List.of();
        }
        return result;
    }

    private void warnIfLanguageMismatch(UserLanguage expectedLanguage, String actualIso) {
        if (expectedLanguage == null || isBlank(actualIso)) {
            return;
        }
        if (!actualIso.equalsIgnoreCase(expectedLanguage.isoCode())) {
            log.warn("LLM responded in {} but expected {}.", actualIso, expectedLanguage.isoCode());
        }
    }

    private List<RecommendationMovie> postFilter(List<RecommendationMovie> movies,
                                                 UserProfile profile,
                                                 String userText) {
        if (movies == null) {
            return List.of();
        }
        boolean askedFantasy = userText.toLowerCase(Locale.ROOT).contains("фэнтез");
        Set<String> mustGenres = profile.getLikedGenres();
        Set<String> anti = profile.getBlocked();
        List<RecommendationMovie> out = new ArrayList<>();
        for (RecommendationMovie movie : movies) {
            if (movie == null || isBlank(movie.getTitle())) continue;
            Set<String> genres = movie.getGenres();
            if (askedFantasy && (genres == null || genres.stream().noneMatch(g -> eq(g, "fantasy")))) continue;
            if (!mustGenres.isEmpty() && (genres == null || genres.stream().noneMatch(g -> containsIgnoreCase(mustGenres, g))))
                continue;
            if (!anti.isEmpty()) {
                boolean banned = containsIgnoreCase(anti, movie.getTitle())
                        || (genres != null && genres.stream().anyMatch(g -> containsIgnoreCase(anti, g)));
                if (banned) continue;
            }
            out.add(movie);
        }
        return out;
    }

    private static String normalizeJsonPayload(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = stripCodeFence(raw).trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        int opening = trimmed.indexOf('{');
        int closing = trimmed.lastIndexOf('}');
        if (opening >= 0 && closing > opening) {
            return trimmed.substring(opening, closing + 1);
        }
        return trimmed;
    }

    private static String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : "";
    }

    private static boolean containsIgnoreCase(Set<String> set, String val) {
        String v = nvl(val).toLowerCase(Locale.ROOT);
        for (String s : set) if (v.contains(nvl(s).toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private static boolean eq(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    public static final class ParsedResponse {
        private String intro = "";
        private String languageIso = "";
        private List<RecommendationMovie> movies = List.of();
        private String reminder = "";

        public String intro() {
            return intro;
        }

        public String languageIso() {
            return languageIso;
        }

        public List<RecommendationMovie> movies() {
            return movies;
        }

        public String reminder() {
            return reminder;
        }
    }
}
