package com.gnemirko.movieRecsBot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.movieRecsBot.entity.MovieOpinion;
import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.dto.RecResponse;
import com.gnemirko.movieRecsBot.handler.DialogPolicy;
import com.gnemirko.movieRecsBot.util.Jsons;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
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
            out = recommendMarkdown(history, userText, profileSummary, profile);
            dialogPolicy.reset(chatId);
            out = appendOpinionReminder(out);
        } else {
            String next = askOneQuestionOrRecommend(history, userText, profileSummary).trim();
            if ("__RECOMMEND__".equalsIgnoreCase(next)) {
                out = recommendMarkdown(history, userText, profileSummary, profile);
                dialogPolicy.reset(chatId);
                out = appendOpinionReminder(out);
            } else {
                dialogPolicy.countClarifying(chatId);
                out = escapeMdV2(next);
            }
        }
        userContextService.append(chatId, "User: " + userText);
        userContextService.append(chatId, "Bot: " + out);
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
                .options(OpenAiChatOptions.builder()
                        .model("gpt-4o-mini")
                        .temperature(0.2)
                        .build())
                .call()
                .content();
    }

    private String recommendMarkdown(String history, String userText, String profileSummary, UserProfile profile) {
        String json = chatClient
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
                .options(OpenAiChatOptions.builder()
                        .model("gpt-4o-mini")
                        .temperature(0.2)
                        .build())
                .call()
                .content();

        try { Jsons.read(json, RecResponse.class); } catch (Exception ignored) {}

        Parsed parsed = parseAndFilter(json, profile, userText);
        if (parsed.movies.isEmpty()) {
            return escapeMdV2("Не нашёл подходящих вариантов. Напишите 1–2 любимых фильма — подберу похожие.");
        }
        StringBuilder sb = new StringBuilder();
        if (!isBlank(parsed.intro)) sb.append(escapeMdV2(parsed.intro)).append("\n\n");
        int i = 1;
        for (Movie m : parsed.movies) {
            String title = escapeMdV2(nvl(m.title));
            String reason = escapeMdV2(nvl(m.reason));
            if (m.year != null) {
                sb.append(i).append(". **").append(title).append(" (").append(m.year).append(")** — ").append(reason).append("\n");
            } else {
                sb.append(i).append(". **").append(title).append("** — ").append(reason).append("\n");
            }
            if (++i > 5) break;
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
            out.movies = List.of();
        }
        if (out.movies == null) out.movies = List.of();
        return out;
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
            if (!mustGenres.isEmpty() && (m.genres == null || m.genres.stream().noneMatch(g -> containsIgnoreCase(mustGenres, g)))) continue;
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
        if (!p.getLikedDirectors().isEmpty()) sb.append("Любимые режиссёры: ").append(p.getLikedDirectors()).append(". ");
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
        String reminder = escapeMdV2("Когда посмотришь фильм, напиши /watched и поделись мнением — я буду точнее.");
        return text + "\n\n" + reminder;
    }

    private static final String MDV2_SPECIAL = "_*[]()~`>#+-=|{}.!";

    private static String escapeMdV2(String s) {
        if (s == null || s.isBlank()) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            if (MDV2_SPECIAL.indexOf(c) >= 0) out.append('\\');
            out.append(c);
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
