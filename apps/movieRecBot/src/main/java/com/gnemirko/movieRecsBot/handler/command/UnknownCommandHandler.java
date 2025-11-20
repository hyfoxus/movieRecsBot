package com.gnemirko.movieRecsBot.handler.command;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Component
public class UnknownCommandHandler {

    public SendMessage handle(CommandContext context) {
        return SendMessage.builder()
                .chatId(String.valueOf(context.chatId()))
                .text("Неизвестная команда\\. /help")
                .parseMode("MarkdownV2")
                .disableWebPagePreview(true)
                .build();
    }
}
