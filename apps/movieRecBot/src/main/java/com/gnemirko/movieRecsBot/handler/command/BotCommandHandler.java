package com.gnemirko.movieRecsBot.handler.command;

import org.telegram.telegrambots.meta.api.methods.BotApiMethod;

public interface BotCommandHandler {

    boolean supports(String command);

    BotApiMethod<?> handle(CommandContext context);
}
