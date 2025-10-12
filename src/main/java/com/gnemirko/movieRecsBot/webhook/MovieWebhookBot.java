package com.gnemirko.movieRecsBot.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;


@Component
public class MovieWebhookBot extends DefaultAbsSender {

    @Value("${telegram.bot.token}")
    private String token;

    public MovieWebhookBot() {
        super(new DefaultBotOptions());
    }

    @Override
    public String getBotToken() {
        return token;
    }
}