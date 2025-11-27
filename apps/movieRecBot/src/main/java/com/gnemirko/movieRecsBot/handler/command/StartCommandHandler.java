package com.gnemirko.movieRecsBot.handler.command;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Component
class StartCommandHandler implements BotCommandHandler {

    @Override
    public boolean supports(String command) {
        return "/start".equals(command);
    }

    @Override
    public SendMessage handle(CommandContext context) {
        return SendMessage.builder()
                .chatId(String.valueOf(context.chatId()))
                .text("👋 Привет! Напиши жанр/настроение и подберу тебе фильм!")
                .disableWebPagePreview(true)
                .build();
    }
}
