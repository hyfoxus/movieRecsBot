package com.gnemirko.movieRecsBot.config;

import com.gnemirko.movieRecsBot.webhook.MovieWebhookBot;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class TelegramConfig {

    @Value("${telegram.bot.webhook-url:}")
    private String webhookUrl;

    @Value("${telegram.bot.webhook-path:/tg/webhook}")
    private String webhookPath;

    private final MovieWebhookBot bot;

    @PostConstruct
    public void init() throws Exception {
        String url = buildWebhookUrl(webhookUrl, webhookPath);
        if (url == null) {
            log.warn("Telegram webhook URL is not configured. Set TELEGRAM_WEBHOOK_URL to enable updates.");
            return;
        }
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(bot, SetWebhook.builder()
                .url(url)
                .build());
        log.info("Telegram webhook registered at {}", url);
    }

    private static String buildWebhookUrl(String base, String path) {
        if (base == null || base.isBlank()) return null;
        String trimmedBase = stripTrailingSlash(base.trim());
        String normalizedPath = normalizePath(path);

        if (normalizedPath.isEmpty()) return trimmedBase;
        if (trimmedBase.endsWith(normalizedPath)) return trimmedBase;
        return trimmedBase + normalizedPath;
    }

    private static String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return "";
        String trimmed = rawPath.trim();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private static String stripTrailingSlash(String value) {
        String out = value;
        while (out.endsWith("/")) out = out.substring(0, out.length() - 1);
        return out;
    }
}
