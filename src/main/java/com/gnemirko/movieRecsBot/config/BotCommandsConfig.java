package com.gnemirko.movieRecsBot.config;

import com.gnemirko.movieRecsBot.webhook.MovieWebhookBot;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BotCommandsConfig {

    private final MovieWebhookBot bot;

    @EventListener(ApplicationReadyEvent.class)
    public void register() throws Exception {
        var list = List.of(
                new BotCommand("/profile", "Показать текущие настройки"),
                new BotCommand("/like_genre", "Добавить жанры"),
                new BotCommand("/like_actor", "Добавить актёров"),
                new BotCommand("/like_director", "Добавить режиссёров"),
                new BotCommand("/block", "Добавить анти-предпочтения"),
                new BotCommand("/unblock", "Убрать метку из любого списка"),
                new BotCommand("/reset_profile", "Очистить профиль"),
                new BotCommand("/help", "Краткая справка")
        );
        bot.execute(SetMyCommands.builder()
                .commands(list)
                .scope(new BotCommandScopeDefault())
                .build());
    }
}