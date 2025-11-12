package com.gnemirko.movieRecsBot.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TelegramWebhookProperties {

    @Getter
    private final String normalizedPath;

    public TelegramWebhookProperties(@Value("${telegram.bot.webhook-path:/tg/webhook}") String rawPath) {
        this.normalizedPath = normalizePath(rawPath);
    }

    @PostConstruct
    void logPath() {
        log.info("Telegram webhook HTTP path resolved to '{}'", normalizedPath);
    }

    private String normalizePath(String rawPath) {
        String fallback = "/tg/webhook";
        if (rawPath == null || rawPath.isBlank()) {
            return fallback;
        }
        String trimmed = rawPath.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}
