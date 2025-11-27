package com.gnemirko.movieRecsBot.handler.command;

import com.gnemirko.movieRecsBot.handler.ProfileCommandHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Component
@RequiredArgsConstructor
class HelpCommandHandler implements BotCommandHandler {

    private final ProfileCommandHandler profileCommandHandler;

    @Override
    public boolean supports(String command) {
        return "/help".equals(command);
    }

    @Override
    public SendMessage handle(CommandContext context) {
        return SendMessage.builder()
                .chatId(String.valueOf(context.chatId()))
                .text(profileCommandHandler.helpText())
                .parseMode("MarkdownV2")
                .disableWebPagePreview(true)
                .build();
    }
}
