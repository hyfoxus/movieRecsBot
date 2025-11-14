package com.gnemirko.movieRecsBot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.movieRecsBot.dto.RecResponse;
import com.gnemirko.movieRecsBot.entity.MovieOpinion;
import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.handler.DialogPolicy;
import com.gnemirko.movieRecsBot.mcp.MovieContextService;
import com.gnemirko.movieRecsBot.util.Jsons;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.gnemirko.movieRecsBot.service.TelegramMessageFormatter.escapeHtml;
import static com.gnemirko.movieRecsBot.service.TelegramMessageFormatter.htmlToPlain;
import static com.gnemirko.movieRecsBot.service.TelegramMessageFormatter.sanitize;
import static com.gnemirko.movieRecsBot.service.TelegramMessageFormatter.scrubMarkdownArtifacts;
import static com.gnemirko.movieRecsBot.service.TelegramMessageFormatter.stripCodeFence;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecommendationService {

    private final ChatClient chatClient;
    private final UserContextService userContextService;
    private final DialogPolicy dialogPolicy;
    private final UserProfileService userProfileService;
    private final MovieContextService movieContextService;
    private final LanguageDetectionService languageDetectionService;

    private static final String BASE_SYSTEM = """
            You are MovieMate, a movie recommendation assistant.

            RULES:
            1) Ask at most TWO clarifying questions per conversation. If the user explicitly says “give recommendations” or “no questions” — go straight to recommendations.
            2) When the info is scarce, make reasonable assumptions (modern, not kids content, any language) and still respond with recommendations.
            3) Do not repeat questions and never ask another question after the user requests recommendations.
            4) Always honor the user’s genre preferences and block lists.
            5) Keep it short: 3–5 movies, each with one concise reason it fits.
            6) Stay on the movie topic (genres, vibe, actors, recent watches) to understand the request better.
            7) Be honest about metadata: never make up cast, release year, or genre details.

            FORMAT FOR TELEGRAM (HTML):
            - Start with a one-line intro.
            - Then provide a numbered list.
            - Emphasize title and year in bold: <b>Title (Year)</b>.
            - No links, code blocks, or unnecessary symbols.
            """;

    private static final String ASK_OR_RECOMMEND_PROMPT = """
            If you already have enough information to recommend, reply with exactly "__RECOMMEND__".
            Otherwise ask exactly one new clarifying question with no preface and no numbering.
            """;

    private static final String JSON_RESPONSE_PROMPT = """
            Return ONLY JSON with this structure:
            {
              "intro": "short intro",
              "movies": [
                {"title":"...", "year":1999, "reason":"...", "genres":["Fantasy","..."]}
              ]
            }
            Strictly 3–5 movies. Obey all user genre preferences and block lists.
            """;

    private static final String NO_MATCH_TEMPLATE = "I couldn’t find a good match. Share 1–2 favorites and I’ll suggest something similar.";
    private static final String REMINDER_TEMPLATE = "When you watch something, send /watched with your thoughts so I can improve.";

    private final Map<String, String> helperTranslations = new ConcurrentHashMap<>();

    public String reply(long chatId, String userText) {
        UserProfile profile = userProfileService.getOrCreate(chatId);
        UserLanguage language = languageDetectionService.detect(userText);
        String profileSummary = buildProfileSummary(profile);
        String history = userContextService.historyAsOneString(chatId, 30, 300);
        String movieContext = movieContextService.buildContextBlock(userText, profileSummary, profile);
        boolean force = dialogPolicy.recommendNow(chatId, userText);
        String out;
        if (force) {
            out = renderRecommendations(history, userText, profileSummary, movieContext, profile, language);
            dialogPolicy.reset(chatId);
            out = appendOpinionReminder(out, language);
        } else {
            String next = stripCodeFence(askOneQuestionOrRecommend(history, userText, profileSummary, movieContext, language)).trim();
            if ("__RECOMMEND__".equalsIgnoreCase(next)) {
                out = renderRecommendations(history, userText, profileSummary, movieContext, profile, language);
                dialogPolicy.reset(chatId);
                out = appendOpinionReminder(out, language);
            } else {
                dialogPolicy.countClarifying(chatId);
                out = sanitize(next);
            }
        }
        userContextService.append(chatId, "User: " + userText);
        userContextService.append(chatId, "Bot: " + htmlToPlain(out));
        return out;
    }

    private String askOneQuestionOrRecommend(String history,
                                             String userText,
                                             String profileSummary,
                                             String movieContext,
                                             UserLanguage language) {
        return chatClient
                .prompt()
                .system(buildSystemPrompt(language, userText, ASK_OR_RECOMMEND_PROMPT))
                .user(enrich(history, userText, profileSummary, movieContext))
                .call()
                .content();
    }

    private String renderRecommendations(String history,
                                         String userText,
                                         String profileSummary,
                                         String movieContext,
                                         UserProfile profile,
                                         UserLanguage language) {
        String raw = chatClient
                .prompt()
                .system(buildSystemPrompt(language, userText, JSON_RESPONSE_PROMPT))
                .user(enrich(history, userText, profileSummary, movieContext))
                .call()
                .content();
        String json = normalizeJsonPayload(raw);

        try {
            Jsons.read(json, RecResponse.class);
        } catch (Exception e) {
            log.debug("LLM response failed RecResponse validation: {}", e.getMessage());
        }

        Parsed parsed = parseAndFilter(json, profile, userText);
        if (parsed.movies.isEmpty()) {
            return sanitize(localizedHelperText(NO_MATCH_TEMPLATE, "noMovies", language));
        }
        StringBuilder sb = new StringBuilder();
        if (!isBlank(parsed.intro)) {
            sb.append("<b>").append(escapeHtml(stripMarkdown(parsed.intro))).append("</b>\n\n");
        }
        int idx = 1;
        for (Movie movie : parsed.movies) {
            if (idx > 5) break;

            String rawTitle = stripMarkdown(nvl(movie.title).trim());
            sb.append(idx).append(". <b>").append(escapeHtml(rawTitle));
            if (movie.year != null && !containsYear(rawTitle, movie.year)) {
                sb.append(" (").append(movie.year).append(")");
            }
            sb.append("</b> — ").append(escapeHtml(stripMarkdown(nvl(movie.reason)))).append("\n\n");
            idx++;
        }
        return scrubMarkdownArtifacts(sb.toString().trim());
    }

    private Parsed parseAndFilter(String json, UserProfile profile, String userText) {
        Parsed out = new Parsed();
        try {
            ObjectMapper om = new ObjectMapper();
            JsonNode root = om.readTree(json);
            out.intro = text(root.get("intro"));
            JsonNode arr = root.get("movies");
            if (arr != null && arr.isArray()) {
                List<Movie> all = new ArrayList<>();
                for (JsonNode n : arr) {
                    Movie m = new Movie();
                    m.title = text(n.get("title"));
                    m.reason = text(n.get("reason"));
                    m.year = n.hasNonNull("year") ? n.get("year").asInt() : null;
                    Set<String> genres = new HashSet<>();
                    JsonNode gs = n.get("genres");
                    if (gs != null && gs.isArray()) {
                        for (JsonNode g : gs) if (g.isTextual()) genres.add(g.asText());
                    }
                    m.genres = genres;
                    if (!isBlank(m.title)) all.add(m);
                }
                out.movies = postFilter(all, profile, userText);
            }
        } catch (Exception e) {
            log.debug("Failed to parse LLM JSON payload: {}", e.getMessage());
            out.movies = List.of();
        }
        if (out.movies == null) out.movies = List.of();
        return out;
    }

    private String normalizeJsonPayload(String raw) {
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

    private List<Movie> postFilter(List<Movie> movies, UserProfile profile, String userText) {
        if (movies == null) return List.of();
        boolean askedFantasy = userText.toLowerCase(Locale.ROOT).contains("фэнтез");
        Set<String> mustGenres = profile.getLikedGenres();
        Set<String> anti = profile.getBlocked();
        List<Movie> out = new ArrayList<>();
        for (Movie m : movies) {
            if (m == null || isBlank(m.title)) continue;
            if (askedFantasy && (m.genres == null || m.genres.stream().noneMatch(g -> eq(g, "fantasy")))) continue;
            if (!mustGenres.isEmpty() && (m.genres == null || m.genres.stream().noneMatch(g -> containsIgnoreCase(mustGenres, g))))
                continue;
            if (!anti.isEmpty()) {
                boolean banned = containsIgnoreCase(anti, m.title) || (m.genres != null && m.genres.stream().anyMatch(g -> containsIgnoreCase(anti, g)));
                if (banned) continue;
            }
            out.add(m);
        }
        return out;
    }

    private String enrich(String history, String userText, String profileSummary, String movieContext) {
        StringBuilder sb = new StringBuilder();
        if (!history.isBlank()) sb.append("История (сокр.):\n").append(history).append("\n\n");
        if (!profileSummary.isBlank()) sb.append("Профиль пользователя:\n").append(profileSummary).append("\n\n");
        if (movieContext != null && !movieContext.isBlank()) {
            sb.append(movieContext.trim()).append("\n\n");
        }
        sb.append("Пользователь: ").append(userText);
        return sb.toString();
    }

    private String buildProfileSummary(UserProfile p) {
        StringBuilder sb = new StringBuilder();
        if (!p.getLikedGenres().isEmpty()) sb.append("Желаемые жанры: ").append(p.getLikedGenres()).append(". ");
        if (!p.getLikedActors().isEmpty()) sb.append("Любимые актёры: ").append(p.getLikedActors()).append(". ");
        if (!p.getLikedDirectors().isEmpty())
            sb.append("Любимые режиссёры: ").append(p.getLikedDirectors()).append(". ");
        if (!p.getBlocked().isEmpty()) sb.append("Не предлагать: ").append(p.getBlocked()).append(". ");
        if (!p.getWatchedMovies().isEmpty()) {
            String watched = p.getWatchedMovies().stream()
                    .limit(5)
                    .map(this::shortOpinion)
                    .collect(Collectors.joining("; "));
            if (!watched.isEmpty()) sb.append("Недавние отзывы: ").append(watched).append(". ");
        }
        return sb.toString().trim();
    }

    private static String text(JsonNode n) {
        return n != null && n.isTextual() ? n.asText() : "";
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

    private static boolean containsYear(String title, Integer year) {
        if (title == null || year == null) return false;
        String normalized = title.replaceAll("[^0-9]", "");
        return normalized.contains(String.valueOf(year));
    }

    private static String stripMarkdown(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.replaceAll("[\\*_`~]+", "");
    }

    private String shortOpinion(MovieOpinion op) {
        if (op == null) return "";
        String title = nvl(op.getTitle()).trim();
        String review = nvl(op.getOpinion()).trim();
        if (title.isEmpty() && review.isEmpty()) return "";
        if (review.isEmpty()) return title;
        return title + " — " + review;
    }

    private String appendOpinionReminder(String text, UserLanguage language) {
        if (text == null || text.isBlank()) return text;
        String reminderText = localizedHelperText(REMINDER_TEMPLATE, "reminder", language);
        String reminder = "<i>" + escapeHtml(reminderText) + "</i>";
        return scrubMarkdownArtifacts(text) + "\n\n" + reminder;
    }

    private String buildSystemPrompt(UserLanguage language, String userText, String taskSpecificPrompt) {
        String directive = language.directive(userText);
        return BASE_SYSTEM + "\n\nLANGUAGE RULE:\n" + directive + "\n\n" + taskSpecificPrompt;
    }

    private String localizedHelperText(String english, String keyPrefix, UserLanguage language) {
        if (!language.requiresTranslation()) {
            return english;
        }
        String cacheKey = keyPrefix + "|" + language.isoCode();
        return helperTranslations.computeIfAbsent(cacheKey, k -> translateHelperText(english, language));
    }

    private String translateHelperText(String english, UserLanguage language) {
        try {
            String instruction = "Translate the following helper text into " + language.displayName() + ". Respond with the translation only.";
            return stripCodeFence(chatClient.prompt().system(instruction).user(english).call().content()).trim();
        } catch (Exception e) {
            log.warn("Failed to translate helper text to {}: {}", language.displayName(), e.getMessage());
            return english;
        }
    }

    private static final class Parsed {
        String intro = "";
        List<Movie> movies = new ArrayList<>();
    }

    private static final class Movie {
        String title;
        Integer year;
        String reason;
        Set<String> genres;
    }
}
