package com.gnemirko.movieRecsBot.webhook;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramWebhookBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import com.gnemirko.movieRecsBot.handler.UpdateRouter;

@Component
@RequiredArgsConstructor
public class MovieWebhookBot extends TelegramWebhookBot {

    @Value("${telegram.bot.username}")
    private String username;

    @Value("${telegram.bot.token}")
    private String token;

    @Value("${telegram.bot.webhook-path}")
    private String path;

    private final UpdateRouter router;

    @Override public String getBotUsername() { return username; }
    @Override public String getBotToken() { return token; }
    @Override public String getBotPath() { return path; }

    @Override
    public BotApiMethod<?> onWebhookUpdateReceived(Update update) {
        return router.handle(update);
    }
}