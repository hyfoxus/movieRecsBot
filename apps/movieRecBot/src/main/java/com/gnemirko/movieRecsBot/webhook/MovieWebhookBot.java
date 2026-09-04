package com.gnemirko.movieRecsBot.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;

import java.util.Objects;

@Component
public class MovieWebhookBot extends DefaultAbsSender {

    private final String token;

    public MovieWebhookBot(@Value("${telegram.bot.token:}") String token) {
        super(new DefaultBotOptions());
        this.token = Objects.requireNonNullElse(token, "");
    }

    @Override
    public String getBotToken() {
        return token;
    }
}
