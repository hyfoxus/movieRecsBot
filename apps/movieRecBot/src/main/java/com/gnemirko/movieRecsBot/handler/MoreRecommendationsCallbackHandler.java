package com.gnemirko.movieRecsBot.handler;

import com.gnemirko.movieRecsBot.service.ChatActionNotifier;
import com.gnemirko.movieRecsBot.service.TaskManagerService;
import com.gnemirko.movieRecsBot.service.recommendation.RecommendationSessionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.Optional;

/**
 * Handles the "🔁 Ещё похожие" button ({@code "more:go"}) by re-running the same original request
 * that produced the last batch — {@link RecommendationSessionStore}'s exclusion tracking then keeps
 * the new batch from repeating what was already shown.
 */
@Component
@RequiredArgsConstructor
public class MoreRecommendationsCallbackHandler {

    private final RecommendationSessionStore recommendationSessionStore;
    private final TaskManagerService taskManagerService;
    private final ChatActionNotifier chatActionNotifier;

    public BotApiMethod<?> handle(CallbackQuery callbackQuery) {
        if (callbackQuery == null || callbackQuery.getMessage() == null) {
            return null;
        }
        long chatId = callbackQuery.getMessage().getChatId();

        Optional<RecommendationSessionStore.Session> session = recommendationSessionStore.get(chatId);
        if (session.isEmpty()) {
            return toast(callbackQuery.getId(), "Сначала попроси рекомендацию 🙂");
        }

        chatActionNotifier.typing(chatId);
        taskManagerService.enqueue(chatId, null, session.get().lastPromptText());
        return toast(callbackQuery.getId(), "Уже ищу ещё варианты…");
    }

    private AnswerCallbackQuery toast(String callbackQueryId, String text) {
        return AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .text(text)
                .showAlert(false)
                .build();
    }
}
