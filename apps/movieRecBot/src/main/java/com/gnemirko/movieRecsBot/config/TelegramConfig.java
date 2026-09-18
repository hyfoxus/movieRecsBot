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

    @Value("${telegram.bot.webhook-secret:}")
    private String webhookSecret;

    @PostConstruct
    public void init() {
        if (botToken == null || botToken.isBlank()) {
            log.error("Telegram bot token is missing. Set TELEGRAM_BOT_TOKEN before starting the bot.");
            throw new IllegalStateException("Telegram bot token is missing");
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Telegram webhook secret is missing. Set TELEGRAM_WEBHOOK_SECRET before starting the bot " +
                    "so incoming updates can be verified as genuinely coming from Telegram.");
            throw new IllegalStateException("Telegram webhook secret is missing");
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

            sender.execute(buildSetWebhook(url));

            log.info("✓ Telegram webhook registered at {}", url);
        } catch (TelegramApiException e) {
            log.error("Failed to set webhook", e);
            throw new RuntimeException("Failed to configure Telegram webhook", e);
        }
    }

    SetWebhook buildSetWebhook(String url) {
        return SetWebhook.builder()
                .url(url)
                .secretToken(webhookSecret)
                .build();
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
