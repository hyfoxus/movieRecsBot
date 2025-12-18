package com.gnemirko.movieRecsBot.config;

import com.gnemirko.movieRecsBot.webhook.MovieWebhookBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telegram.bot.enable-webhook", havingValue = "true", matchIfMissing = true)
public class BotCommandsConfig {

    private final MovieWebhookBot bot;

    @Value("${telegram.bot.token:}")
    private String botToken;

    @Value("${telegram.bot.webhook-url:}")
    private String webhookUrl;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        if (botToken == null || botToken.isBlank()) {
            log.warn("Bot token is not configured. Skipping command registration.");
            return;
        }

        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Webhook URL is not configured. Skipping command registration.");
            return;
        }

        int maxRetries = 5;
        int baseDelay = 3000; // Increased from 2000

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                long delayMs = baseDelay * (long) Math.pow(1.5, attempt - 1);
                Thread.sleep(delayMs);

                log.info("Attempting to register bot commands (attempt {}/{})", attempt, maxRetries);

                var commands = List.of(
                        new BotCommand("/profile", "Показать текущие настройки"),
                        new BotCommand("/like_genre", "Добавить жанры"),
                        new BotCommand("/like_actor", "Добавить актёров"),
                        new BotCommand("/like_director", "Добавить режиссёров"),
                        new BotCommand("/block", "Добавить анти-предпочтения"),
                        new BotCommand("/unblock", "Убрать метку из любого списка"),
                        new BotCommand("/reset_profile", "Очистить профиль"),
                        new BotCommand("/help", "Краткая справка"),
                        new BotCommand("/menu", "Меню профиля"),
                        new BotCommand("/report", "Сообщить о проблеме")
                );

                bot.execute(SetMyCommands.builder()
                        .commands(commands)
                        .scope(new BotCommandScopeDefault())
                        .build());

                log.info("✓ Bot commands registered successfully on attempt {}", attempt);
                return;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Command registration interrupted", e);
                return;
            } catch (Exception e) {
                if (attempt == maxRetries) {
                    log.error("✗ Failed to register bot commands after {} attempts. " +
                            "Bot will still work, but commands won't appear in Telegram menu. " +
                            "Error: {}", maxRetries, e.getMessage());
                    log.debug("Full error trace:", e);
                } else {
                    log.warn("Failed to register bot commands on attempt {}/{}. Error: {}. Retrying...",
                            attempt, maxRetries, e.getMessage());
                }
            }
        }
    }
}
