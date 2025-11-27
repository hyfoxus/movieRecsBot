package com.gnemirko.movieRecsBot.handler.command;

import com.gnemirko.movieRecsBot.handler.ProfileReplyBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;

@Component
@RequiredArgsConstructor
class ProfileCommand implements BotCommandHandler {

    private final ProfileReplyBuilder profileReplyBuilder;

    @Override
    public boolean supports(String command) {
        return "/profile".equals(command);
    }

    @Override
    public BotApiMethod<?> handle(CommandContext context) {
        return profileReplyBuilder.profileMessage(context.chatId());
    }
}
