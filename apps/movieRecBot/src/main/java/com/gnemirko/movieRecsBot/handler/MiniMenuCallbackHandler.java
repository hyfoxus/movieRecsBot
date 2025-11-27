package com.gnemirko.movieRecsBot.handler;

import com.gnemirko.movieRecsBot.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

@Component
@RequiredArgsConstructor
public class MiniMenuCallbackHandler {

    private final UserProfileService userProfileService;
    private final MenuStateService menuStateService;
    private final MiniMenu miniMenu;
    private final PromptService promptService;
    private final ProfileReplyBuilder profileReplyBuilder;

    public BotApiMethod<?> handle(CallbackQuery callbackQuery) {
        if (callbackQuery == null || callbackQuery.getMessage() == null) {
            return null;
        }
        long chatId = callbackQuery.getMessage().getChatId();
        String data = String.valueOf(callbackQuery.getData());

        return switch (data) {
            case "menu:show" -> profileReplyBuilder.profileMessage(chatId);
            case "menu:add_genre" ->
                    promptService.prompt(chatId, "Введи жанры через запятую.", MenuStateService.Await.ADD_GENRE);
            case "menu:add_actor" ->
                    promptService.prompt(chatId, "Введи актёров через запятую.", MenuStateService.Await.ADD_ACTOR);
            case "menu:add_director" ->
                    promptService.prompt(chatId, "Введи режиссёров через запятую.", MenuStateService.Await.ADD_DIRECTOR);
            case "menu:add_block" ->
                    promptService.prompt(chatId, "Введи анти-метки через запятую.", MenuStateService.Await.ADD_BLOCK);
            case "menu:add_opinion" -> promptService.prompt(chatId,
                    "Напиши название фильма на первой строке, мнение — на второй. Пример:\nInception\nОчень понравился.",
                    MenuStateService.Await.ADD_OPINION);
            case "menu:reset" -> resetProfile(chatId);
            default -> SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text("Неизвестное действие\\.")
                    .parseMode("MarkdownV2")
                    .disableWebPagePreview(true)
                    .build();
        };
    }

    private BotApiMethod<?> resetProfile(long chatId) {
        userProfileService.reset(chatId);
        menuStateService.clear(chatId);
        return SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text("Профиль очищен\\.")
                .parseMode("MarkdownV2")
                .replyMarkup(miniMenu.mainMenu())
                .disableWebPagePreview(true)
                .build();
    }
}
