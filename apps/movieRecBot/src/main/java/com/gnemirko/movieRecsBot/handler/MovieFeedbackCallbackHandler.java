package com.gnemirko.movieRecsBot.handler;

import com.gnemirko.movieRecsBot.service.UserProfileService;
import com.gnemirko.movieRecsBot.service.recommendation.RecommendationMovie;
import com.gnemirko.movieRecsBot.service.recommendation.RecommendationSessionStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.util.List;
import java.util.Optional;

/**
 * Handles the per-movie 👍/👎 buttons attached to a recommendation batch ({@code "rate:<idx>:up|down"}).
 * Feedback is genre-based only: {@link RecommendationMovie} carries no actor field, so actor-based
 * feedback would require extending the LLM JSON contract first.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MovieFeedbackCallbackHandler {

    private final RecommendationSessionStore recommendationSessionStore;
    private final UserProfileService userProfileService;

    public BotApiMethod<?> handle(CallbackQuery callbackQuery) {
        if (callbackQuery == null || callbackQuery.getMessage() == null) {
            return null;
        }
        long chatId = callbackQuery.getMessage().getChatId();
        String data = String.valueOf(callbackQuery.getData());
        String[] parts = data.split(":");
        if (parts.length != 3) {
            return toast(callbackQuery.getId(), "Не понял оценку 🙁");
        }

        int idx;
        try {
            idx = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return toast(callbackQuery.getId(), "Не понял оценку 🙁");
        }
        boolean liked = "up".equals(parts[2]);

        Optional<RecommendationSessionStore.Session> session = recommendationSessionStore.get(chatId);
        if (session.isEmpty()) {
            return toast(callbackQuery.getId(), "Эта рекомендация устарела 🙁");
        }
        List<RecommendationMovie> movies = session.get().lastMovies();
        if (idx < 0 || idx >= movies.size()) {
            return toast(callbackQuery.getId(), "Эта рекомендация устарела 🙁");
        }

        RecommendationMovie movie = movies.get(idx);
        List<String> genres = movie.getGenres() == null ? List.of() : movie.getGenres().stream().toList();
        if (!genres.isEmpty()) {
            if (liked) {
                userProfileService.addGenres(chatId, genres);
            } else {
                userProfileService.blockTags(chatId, genres);
            }
        }
        log.debug("Recorded {} feedback for chat {} on movie index {} ({})", liked ? "like" : "block", chatId, idx, movie.getTitle());

        String confirmation = liked ? "Учту, что тебе понравилось 👍" : "Учту, чтобы не предлагать похожее 👎";
        return toast(callbackQuery.getId(), confirmation);
    }

    private AnswerCallbackQuery toast(String callbackQueryId, String text) {
        return AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .text(text)
                .showAlert(false)
                .build();
    }
}
