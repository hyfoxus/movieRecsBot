package com.gnemirko.movieRecsBot.handler.command;

import com.gnemirko.movieRecsBot.handler.MiniMenu;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Component
@RequiredArgsConstructor
class MenuCommandHandler implements BotCommandHandler {

    private final MiniMenu miniMenu;

    @Override
    public boolean supports(String command) {
        return "/menu".equals(command);
    }

    @Override
    public SendMessage handle(CommandContext context) {
        return SendMessage.builder()
                .chatId(String.valueOf(context.chatId()))
                .text("Что меняем? Выбери действие ниже.")
                .replyMarkup(miniMenu.mainMenu())
                .disableWebPagePreview(true)
                .build();
    }
}
