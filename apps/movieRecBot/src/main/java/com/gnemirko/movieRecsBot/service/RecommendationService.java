package com.gnemirko.movieRecsBot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.movieRecsBot.dto.RecResponse;
import com.gnemirko.movieRecsBot.entity.MovieOpinion;
import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.handler.DialogPolicy;
import com.gnemirko.movieRecsBot.util.Jsons;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecommendationService {

    private final ChatClient chatClient;
    private final UserContextService userContextService;
    private final DialogPolicy dialogPolicy;
    private final UserProfileService userProfileService;

    private static final String SYSTEM = """
            Ты — MovieMate, помощник по рекомендациям фильмов.
            
            ПРАВИЛА:
            1) Разрешено задать максимум ДВА уточняющих вопроса за весь диалог. Если пользователь просит «дай рекомендации» или «не задавай вопросы» — сразу выдай рекомендации.
            2) Если данных мало — сделай разумные предположения (современные, не детские, язык любой) и выдай рекомендации.
            3) Не повторяй вопросы. Не задавай вопросы после явного запроса рекомендаций.
            4) Всегда соблюдай жанровые и анти-предпочтения пользователя. Если запросил фэнтези — не предлагай нефэнтези.
            5) Краткость: 3–5 фильмов, для каждого — 1 короткое объяснение, почему подходит.
            6) Для ответа используй тот же язык, что использовал в последнем сообщении пользователь.
            
            ФОРМАТ ДЛЯ TELEGRAM (MarkdownV2):
            - Вступление — одна строка.
            - Далее пронумерованный список.
            - Название и год выделяй жирным: **Название (Год)**.
            - Без ссылок и кода. Минимум спецсимволов.
            """;

    public String reply(long chatId, String userText) {
        UserProfile profile = userProfileService.getOrCreate(chatId);
        String profileSummary = buildProfileSummary(profile);
        String history = userContextService.historyAsOneString(chatId, 30, 300);
        boolean force = dialogPolicy.recommendNow(chatId, userText);
        String out;
        if (force) {
            out = renderRecommendations(history, userText, profileSummary, profile);
            dialogPolicy.reset(chatId);
            out = appendOpinionReminder(out);
        } else {
            String next = stripCodeFence(askOneQuestionOrRecommend(history, userText, profileSummary)).trim();
            if ("__RECOMMEND__".equalsIgnoreCase(next)) {
                out = renderRecommendations(history, userText, profileSummary, profile);
                dialogPolicy.reset(chatId);
                out = appendOpinionReminder(out);
            } else {
                dialogPolicy.countClarifying(chatId);
                out = escapeHtml(next);
            }
        }
        userContextService.append(chatId, "User: " + userText);
        userContextService.append(chatId, "Bot: " + htmlToPlain(out));
        return out;
    }

    private String askOneQuestionOrRecommend(String history, String userText, String profileSummary) {
        return chatClient
                .prompt()
                .system(SYSTEM + """
                        
                        Если информации достаточно — ответь ровно строкой "__RECOMMEND__".
                        Иначе задай ОДИН новый, неповторяющийся вопрос, без предисловий и без нумерации.
                        """)
                .user(enrich(history, userText, profileSummary))
                .call()
                .content();
    }

    private String renderRecommendations(String history, String userText, String profileSummary, UserProfile profile) {
        String raw = chatClient
                .prompt()
                .system(SYSTEM + """
                        
                        Сейчас верни ТОЛЬКО JSON без текста вокруг строго вида:
                        {
                          "intro": "краткое вступление",
                          "movies": [
                            {"title":"...", "year":1999, "reason":"...", "genres":["Fantasy","..."]},
                            ...
                          ]
                        }
                        Строго 3–5 фильмов. Следуй жанровым и анти-ограничениям пользователя.
                        """)
                .user(enrich(history, userText, profileSummary))
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
            return escapeHtml("Не нашёл подходящих вариантов. Напишите 1–2 любимых фильма — подберу похожие.");
        }
        StringBuilder sb = new StringBuilder();
        if (!isBlank(parsed.intro)) {
            sb.append("<b>").append(escapeHtml(parsed.intro)).append("</b>\n\n");
        }
        int idx = 1;
        for (Movie movie : parsed.movies) {
            if (idx > 5) break;

            String rawTitle = stripMarkdown(nvl(movie.title).trim());
            sb.append(idx).append(". <b>").append(escapeHtml(rawTitle));
            if (movie.year != null && !containsYear(rawTitle, movie.year)) {
                sb.append(" (").append(movie.year).append(")");
            }
            sb.append("</b> — ").append(escapeHtml(nvl(movie.reason))).append("\n\n");
            idx++;
        }
        return sb.toString().trim();
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

    private String stripCodeFence(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        String withoutFence = trimmed.replaceFirst("^```[a-zA-Z0-9]*\\s*", "");
        int fenceEnd = withoutFence.lastIndexOf("```");
        if (fenceEnd >= 0) {
            withoutFence = withoutFence.substring(0, fenceEnd);
        }
        return withoutFence.trim();
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

    private String enrich(String history, String userText, String profileSummary) {
        StringBuilder sb = new StringBuilder();
        if (!history.isBlank()) sb.append("История (сокр.):\n").append(history).append("\n\n");
        if (!profileSummary.isBlank()) sb.append("Профиль пользователя:\n").append(profileSummary).append("\n\n");
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
        return value.replaceAll("\\*+", "");
    }

    private static String htmlToPlain(String html) {
        if (html == null || html.isEmpty()) return "";
        String plain = html.replace("<br/>", "\n").replace("<br>", "\n");
        plain = plain.replaceAll("<[^>]+>", "");
        return plain
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private String shortOpinion(MovieOpinion op) {
        if (op == null) return "";
        String title = nvl(op.getTitle()).trim();
        String review = nvl(op.getOpinion()).trim();
        if (title.isEmpty() && review.isEmpty()) return "";
        if (review.isEmpty()) return title;
        return title + " — " + review;
    }

    private String appendOpinionReminder(String text) {
        if (text == null || text.isBlank()) return text;
        String reminder = "<i>" + escapeHtml("Когда посмотришь фильм, напиши /watched и поделись мнением — я буду точнее.") + "</i>";
        return text + "\n\n" + reminder;
    }

    private static String escapeHtml(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                case '\n' -> out.append("<br/>");
                default -> out.append(c);
            }
        }
        return out.toString();
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
