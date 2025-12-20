package com.gnemirko.movieRecsBot.handler.command;

import com.gnemirko.movieRecsBot.service.VersionService;
import com.gnemirko.movieRecsBot.util.CmdText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Component
@RequiredArgsConstructor
class VersionCommandHandler implements BotCommandHandler {

    private final VersionService versionService;

    @Override
    public boolean supports(String command) {
        return "/version".equals(command);
    }

    @Override
    public SendMessage handle(CommandContext context) {
        return SendMessage.builder()
                .chatId(String.valueOf(context.chatId()))
                .text(CmdText.esc(versionService.describe()))
                .parseMode("MarkdownV2")
                .disableWebPagePreview(true)
                .build();
    }
}
