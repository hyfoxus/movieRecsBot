package com.gnemirko.movieRecsBot.handler;

import com.gnemirko.movieRecsBot.service.ChatActionNotifier;
import com.gnemirko.movieRecsBot.service.TaskManagerService;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MoreRecommendationsCallbackHandlerTest {

    @Mock
    private RecommendationSessionStore recommendationSessionStore;
    @Mock
    private TaskManagerService taskManagerService;
    @Mock
    private ChatActionNotifier chatActionNotifier;

    private MoreRecommendationsCallbackHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MoreRecommendationsCallbackHandler(recommendationSessionStore, taskManagerService, chatActionNotifier);
    }

    private CallbackQuery callbackQuery(long chatId) {
        Chat chat = new Chat();
        chat.setId(chatId);
        Message message = new Message();
        message.setChat(chat);
        CallbackQuery cq = new CallbackQuery();
        cq.setId("cb-1");
        cq.setData("more:go");
        cq.setMessage(message);
        return cq;
    }

    @Test
    void reEnqueuesTheOriginalPromptWhenSessionExists() {
        long chatId = 1L;
        when(recommendationSessionStore.get(chatId))
                .thenReturn(Optional.of(new RecommendationSessionStore.Session("cozy noir movie", List.of(), Set.of())));

        BotApiMethod<?> result = handler.handle(callbackQuery(chatId));

        verify(chatActionNotifier).typing(chatId);
        verify(taskManagerService).enqueue(chatId, null, "cozy noir movie");
        assertThat(result).isInstanceOf(AnswerCallbackQuery.class);
    }

    @Test
    void missingSessionSkipsEnqueue() {
        long chatId = 2L;
        when(recommendationSessionStore.get(chatId)).thenReturn(Optional.empty());

        BotApiMethod<?> result = handler.handle(callbackQuery(chatId));

        verify(taskManagerService, never()).enqueue(chatId, null, "");
        verify(chatActionNotifier, never()).typing(chatId);
        assertThat(result).isInstanceOf(AnswerCallbackQuery.class);
    }
}
