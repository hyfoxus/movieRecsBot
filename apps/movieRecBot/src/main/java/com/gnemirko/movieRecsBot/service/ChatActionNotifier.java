package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.webhook.MovieWebhookBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatActionNotifier {

    private final MovieWebhookBot sender;

    /**
     * Best-effort "typing…" indicator shown while a recommendation task is queued/running.
     * Never throws — a failure here should never block the actual reply.
     */
    public void typing(long chatId) {
        try {
            sender.execute(SendChatAction.builder()
                    .chatId(String.valueOf(chatId))
                    .action(ActionType.TYPING.toString())
                    .build());
        } catch (TelegramApiException e) {
            log.debug("Failed to send typing indicator to chat {}: {}", chatId, e.getMessage());
        }
    }
}
