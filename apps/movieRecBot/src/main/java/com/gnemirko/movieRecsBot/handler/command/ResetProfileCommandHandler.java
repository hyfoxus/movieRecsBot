package com.gnemirko.movieRecsBot.handler.command;

import com.gnemirko.movieRecsBot.handler.MenuStateService;
import com.gnemirko.movieRecsBot.handler.MiniMenu;
import com.gnemirko.movieRecsBot.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Component
@RequiredArgsConstructor
class ResetProfileCommandHandler implements BotCommandHandler {

    private final UserProfileService userProfileService;
    private final MenuStateService menuStateService;
    private final MiniMenu miniMenu;

    @Override
    public boolean supports(String command) {
        return "/reset_profile".equals(command);
    }

    @Override
    public SendMessage handle(CommandContext context) {
        userProfileService.reset(context.chatId());
        menuStateService.clear(context.chatId());
        return SendMessage.builder()
                .chatId(String.valueOf(context.chatId()))
                .text("Профиль очищен\\.")
                .parseMode("MarkdownV2")
                .replyMarkup(miniMenu.mainMenu())
                .disableWebPagePreview(true)
                .build();
    }
}
