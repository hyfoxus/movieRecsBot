package com.gnemirko.movieRecsBot.handler.command;

import com.gnemirko.movieRecsBot.handler.StatusCommands;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Component
@RequiredArgsConstructor
class StatusCommandHandler implements BotCommandHandler {

    private final StatusCommands statusCommands;

    @Override
    public boolean supports(String command) {
        return "/status".equals(command);
    }

    @Override
    public SendMessage handle(CommandContext context) {
        String argument = context.argument();
        String response = statusCommands.statusForChat(
                context.chatId(),
                argument == null || argument.isBlank() ? null : argument
        );
        return SendMessage.builder()
                .chatId(String.valueOf(context.chatId()))
                .text(response)
                .parseMode("MarkdownV2")
                .disableWebPagePreview(true)
                .build();
    }
}
