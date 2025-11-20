package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.webhook.MovieWebhookBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import static com.gnemirko.movieRecsBot.service.TelegramMessageFormatter.htmlToPlain;

@Component
@RequiredArgsConstructor
@Slf4j
class TaskNotifier {

    private final MovieWebhookBot sender;

    void send(SendMessage message) {
        try {
            sender.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message: {} | payload='{}'", e.getMessage(), message.getText(), e);
            if ("HTML".equalsIgnoreCase(message.getParseMode())) {
                attemptFallback(message);
            }
        }
    }

    private void attemptFallback(SendMessage original) {
        try {
            SendMessage fallback = SendMessage.builder()
                    .chatId(original.getChatId())
                    .text(htmlToPlain(original.getText()))
                    .disableWebPagePreview(original.getDisableWebPagePreview())
                    .replyMarkup(original.getReplyMarkup())
                    .build();
            sender.execute(fallback);
        } catch (TelegramApiException ex) {
            log.error("Fallback send also failed: {}", ex.getMessage(), ex);
        }
    }
}
