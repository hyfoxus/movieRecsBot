package com.gnemirko.movieRecsBot.handler.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CommandDispatcher {

    private final List<BotCommandHandler> handlers;
    private final UnknownCommandHandler unknownCommandHandler;

    public BotApiMethod<?> dispatch(CommandContext context) {
        for (BotCommandHandler handler : handlers) {
            if (handler.supports(context.command())) {
                return handler.handle(context);
            }
        }
        return unknownCommandHandler.handle(context);
    }
}
