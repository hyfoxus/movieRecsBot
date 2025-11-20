package com.gnemirko.movieRecsBot.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Component
@RequiredArgsConstructor
public class ProfileReplyBuilder {

    private final ProfileCommandHandler profileCommandHandler;
    private final MiniMenu miniMenu;

    public SendMessage profileMessage(long chatId) {
        return SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(profileCommandHandler.profileText(chatId))
                .parseMode("MarkdownV2")
                .replyMarkup(miniMenu.mainMenu())
                .disableWebPagePreview(true)
                .build();
    }
}
