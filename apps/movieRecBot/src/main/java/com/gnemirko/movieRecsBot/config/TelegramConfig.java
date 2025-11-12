package com.gnemirko.movieRecsBot.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.bot.enable-webhook", havingValue = "true", matchIfMissing = true)
public class TelegramConfig {

    @Value("${telegram.bot.webhook-url:}")
    private String webhookUrl;

    @Value("${telegram.bot.webhook-path:/tg/webhook}")
    private String webhookPath;

    @Value("${telegram.bot.token}")
    private String botToken;

    @PostConstruct
    public void init() {
        if (botToken == null || botToken.isBlank()) {
            log.error("Telegram bot token is missing. Set TELEGRAM_BOT_TOKEN before starting the bot.");
            throw new IllegalStateException("Telegram bot token is missing");
        }

        String url = buildWebhookUrl(webhookUrl, webhookPath);
        if (url == null) {
            log.error("Telegram webhook URL is not configured. Set TELEGRAM_WEBHOOK_URL to enable updates.");
            throw new IllegalStateException("Telegram webhook URL is not configured");
        }

        try {
            org.telegram.telegrambots.bots.DefaultAbsSender sender = new org.telegram.telegrambots.bots.DefaultAbsSender(
                    new org.telegram.telegrambots.bots.DefaultBotOptions()) {
                @Override
                public String getBotToken() {
                    return botToken;
                }
            };

            sender.execute(SetWebhook.builder()
                    .url(url)
                    .build());

            log.info("✓ Telegram webhook registered at {}", url);
        } catch (TelegramApiException e) {
            log.error("Failed to set webhook", e);
            throw new RuntimeException("Failed to configure Telegram webhook", e);
        }
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
