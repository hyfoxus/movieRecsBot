package com.gnemirko.movieRecsBot.handler.command;

import org.telegram.telegrambots.meta.api.objects.Update;

public record CommandContext(long chatId, String command, String messageText, Update update) {

    public String argument() {
        if (messageText == null || command == null) {
            return "";
        }
        if (messageText.length() <= command.length()) {
            return "";
        }
        return messageText.substring(command.length()).trim();
    }
}
