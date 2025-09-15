package com.gnemirko.movieRecsBot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import com.gnemirko.movieRecsBot.webhook.MovieWebhookBot;

@Configuration
public class TelegramConfig {

    @Value("${telegram.bot.webhook-url}")
    private String webhookUrl;

    private final MovieWebhookBot bot;

    public TelegramConfig(MovieWebhookBot bot) {
        this.bot = bot;
    }

    @PostConstruct
    public void init() throws Exception {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(bot, SetWebhook.builder().url(webhookUrl).build());
    }
}