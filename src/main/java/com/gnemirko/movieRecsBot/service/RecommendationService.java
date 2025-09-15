package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.handler.DialogPolicy;
import com.gnemirko.movieRecsBot.dto.RecResponse;
import com.gnemirko.movieRecsBot.util.Jsons;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final ChatClient chatClient;
    private final UserContextService userContextService;
    private final DialogPolicy policy;

    private static final String SYSTEM = """
            Ты — MovieMate, помощник по рекомендациям фильмов.

            ПРАВИЛА:
            1) Можно задать максимум ДВА уточняющих вопроса за все время диалога. Если пользователь пишет, что хочет рекомендацию или просит “не задавай вопросы”, перестань спрашивать и сразу дай рекомендации.
            2) Если данных мало — делай разумные предположения по умолчанию (современные, не детские, язык любой, страна любая) и сразу давай рекомендации.
            3) Никогда не повторяй один и тот же вопрос. Не задавай вопросы после явного запроса рекомендаций.
            4) ВСЕГДА проверяй жанровые ограничения пользователя. Если он попросил фэнтези — не предлагай нефэнтези.
            5) Отвечай кратко. Результат — 3–5 фильмов.
            6) Если пользователь недоволен вопросами, немедленно давай рекомендации.

            ФОРМАТ ДЛЯ TELEGRAM:
            - Используй MarkdownV2.
            - Вступление — обычный текст.
            - Далее список фильмов, пронумерованный или с маркерами.
            - Название и год фильма выделяй жирным: **Название (Год)**.
            - После — короткое объяснение в одну строку.
            - Никаких лишних знаков пунктуации, только безопасные для MarkdownV2.
            """;

    public String reply(long chatId, String userText) {
        var history = userContextService.historyAsOneString(chatId, 30, 300);

        boolean forceRecommend = policy.recommendNow(chatId, userText);

        String content;
        if (forceRecommend) {
            content = recommendJson(history, userText);
            policy.reset(chatId);
        } else {
            String next = askOneQuestionOrRecommend(history, userText);
            if (next.startsWith("__RECOMMEND__")) {
                content = recommendJson(history, userText);
                policy.reset(chatId);
            } else {
                policy.countClarifying(chatId);
                content = next;
            }
        }

        userContextService.append(chatId, "User: " + userText);
        userContextService.append(chatId, "Bot: " + content);
        return content;
    }

    private String askOneQuestionOrRecommend(String history, String userText) {
        String answer = chatClient.prompt()
                .system(SYSTEM + "\nЕсли информации уже достаточно — ответь ровно строкой '__RECOMMEND__'. "
                        + "Иначе задай ОДИН новый неповторяющийся вопрос (без предисловий).")
                .user(history.isBlank() ? userText : "История:\n" + history + "\n\nПользователь: " + userText)
                .options(OpenAiChatOptions.builder()
                        .model("gpt-4o-mini")
                        .temperature(0.2)
                        .build())
                .call()
                .content()
                .trim();

        return answer;
    }

    private String recommendJson(String history, String userText) {
        String json = chatClient.prompt()
                .system(SYSTEM + """
                СЕЙЧАС ВЕРНИ ТОЛЬКО JSON без пояснений в формате:
                {
                  "intro": "краткое вступление",
                  "movies": [
                    {"title":"...", "year":1999, "reason":"...", "genres":["Fantasy","..."]},
                    ...
                  ]
                }
                Строго 3–5 фильмов. Жанры включай корректные. Если запросил фэнтези — все элементы 'genres' должны содержать 'Fantasy'.
                """)
                .user(history.isBlank() ? userText : "История:\n" + history + "\n\nПользователь: " + userText)
                .options(OpenAiChatOptions.builder()
                        .model("gpt-4o-mini")
                        .temperature(0.2)
                        .build())
                .call()
                .content();

        RecResponse resp;
        try {
            resp = Jsons.read(json, RecResponse.class);
        } catch (RuntimeException e) {
            return "Готов дать рекомендации, но получил некорректный ответ модели. Попробуйте уточнить 1–2 любимых фильма/актёров.";
        }

        boolean fantasyAsked = userText.toLowerCase().contains("фэнтез");
        if (fantasyAsked) {
            resp = new RecResponse(
                    resp.intro(),
                    resp.movies().stream()
                            .filter(m -> m.genres() != null && m.genres().stream().anyMatch(g -> g.equalsIgnoreCase("Fantasy")))
                            .toList()
            );
        }

        if (resp.movies() == null || resp.movies().isEmpty()) {
            return "Не нашёл подходящих вариантов. Напишите 1-2 любимых фэнтези-фильма — подберу похожие.";
        }

        var sb = new StringBuilder();
        if (resp.intro() != null && !resp.intro().isBlank()) sb.append(resp.intro()).append("\n\n");
        int i = 1;
        for (var m : resp.movies()) {
            sb.append(i++).append(". **").append(m.title());
            if (m.year() != null) sb.append(" (").append(m.year()).append(")");
            sb.append("** — ").append(m.reason()).append("\n\n");
            if (i > 5) break;
        }
        return sb.toString().trim();
    }
}