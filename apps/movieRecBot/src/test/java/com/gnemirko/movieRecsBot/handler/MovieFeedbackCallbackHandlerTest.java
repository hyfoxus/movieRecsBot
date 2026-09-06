package com.gnemirko.movieRecsBot.handler;

import com.gnemirko.movieRecsBot.service.UserProfileService;
import com.gnemirko.movieRecsBot.service.recommendation.RecommendationMovie;
import com.gnemirko.movieRecsBot.service.recommendation.RecommendationSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieFeedbackCallbackHandlerTest {

    @Mock
    private RecommendationSessionStore recommendationSessionStore;
    @Mock
    private UserProfileService userProfileService;

    private MovieFeedbackCallbackHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MovieFeedbackCallbackHandler(recommendationSessionStore, userProfileService);
    }

    private CallbackQuery callbackQuery(long chatId, String data) {
        Chat chat = new Chat();
        chat.setId(chatId);
        Message message = new Message();
        message.setChat(chat);
        CallbackQuery cq = new CallbackQuery();
        cq.setId("cb-1");
        cq.setData(data);
        cq.setMessage(message);
        return cq;
    }

    private RecommendationMovie movie(String title, Set<String> genres) {
        RecommendationMovie m = new RecommendationMovie();
        m.setTitle(title);
        m.setGenres(genres);
        return m;
    }

    @Test
    void thumbsUpAddsMovieGenresAsLiked() {
        long chatId = 1L;
        RecommendationMovie movie = movie("Heat", Set.of("Crime"));
        when(recommendationSessionStore.get(chatId))
                .thenReturn(java.util.Optional.of(new RecommendationSessionStore.Session("prompt", List.of(movie), Set.of())));

        BotApiMethod<?> result = handler.handle(callbackQuery(chatId, "rate:0:up"));

        verify(userProfileService).addGenres(chatId, List.of("Crime"));
        verify(userProfileService, never()).blockTags(chatId, List.of("Crime"));
        assertThat(result).isInstanceOf(AnswerCallbackQuery.class);
    }

    @Test
    void thumbsDownBlocksMovieGenres() {
        long chatId = 2L;
        RecommendationMovie movie = movie("Saw", Set.of("Horror"));
        when(recommendationSessionStore.get(chatId))
                .thenReturn(java.util.Optional.of(new RecommendationSessionStore.Session("prompt", List.of(movie), Set.of())));

        handler.handle(callbackQuery(chatId, "rate:0:down"));

        verify(userProfileService).blockTags(chatId, List.of("Horror"));
        verify(userProfileService, never()).addGenres(chatId, List.of("Horror"));
    }

    @Test
    void missingSessionReturnsToastWithoutTouchingProfile() {
        long chatId = 3L;
        when(recommendationSessionStore.get(chatId)).thenReturn(java.util.Optional.empty());

        BotApiMethod<?> result = handler.handle(callbackQuery(chatId, "rate:0:up"));

        assertThat(result).isInstanceOf(AnswerCallbackQuery.class);
        verify(userProfileService, never()).addGenres(anyLong(), any());
    }

    @Test
    void outOfRangeIndexReturnsToastWithoutTouchingProfile() {
        long chatId = 4L;
        RecommendationMovie movie = movie("Heat", Set.of("Crime"));
        when(recommendationSessionStore.get(chatId))
                .thenReturn(java.util.Optional.of(new RecommendationSessionStore.Session("prompt", List.of(movie), Set.of())));

        BotApiMethod<?> result = handler.handle(callbackQuery(chatId, "rate:9:up"));

        assertThat(result).isInstanceOf(AnswerCallbackQuery.class);
        verify(userProfileService, never()).addGenres(chatId, List.of("Crime"));
    }
}
