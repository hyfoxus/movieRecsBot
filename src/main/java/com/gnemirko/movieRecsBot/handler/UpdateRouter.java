package com.gnemirko.movieRecsBot.handler;

import com.gnemirko.movieRecsBot.service.RecommendationService;
import com.gnemirko.movieRecsBot.service.UserProfileService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import com.gnemirko.movieRecsBot.service.UserProfileService;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ForceReplyKeyboard;


@Component
@RequiredArgsConstructor
public class UpdateRouter {


    private final ProfileCommandHandler profileHandler;
    private final RecommendationService recommendationService;
    private final UserProfileService userProfileService;
    private final MiniMenu miniMenu;
    private final MenuStateService stateService;

    public BotApiMethod<?> handle(Update update) {
        if (update == null) return null;

        // 1) Callback buttons
        if (update.hasCallbackQuery()) {
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            String data = String.valueOf(update.getCallbackQuery().getData());

            return switch (data) {
                case "menu:show" -> SendMessage.builder()
                        .chatId(String.valueOf(chatId))
                        .text(profileHandler.profileText(chatId))
                        .parseMode("MarkdownV2")
                        .replyMarkup(miniMenu.mainMenu())
                        .disableWebPagePreview(true)
                        .build();

                case "menu:add_genre" -> prompt(chatId, "Введи жанры через запятую\\.", MenuStateService.Await.ADD_GENRE);
                case "menu:add_actor" -> prompt(chatId, "Введи актёров через запятую\\.", MenuStateService.Await.ADD_ACTOR);
                case "menu:add_director" -> prompt(chatId, "Введи режиссёров через запятую\\.", MenuStateService.Await.ADD_DIRECTOR);
                case "menu:add_block" -> prompt(chatId, "Введи анти\\-метки через запятую\\.", MenuStateService.Await.ADD_BLOCK);

                case "menu:reset" -> {
                    userProfileService.reset(chatId);
                    stateService.clear(chatId);
                    yield SendMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .text("Профиль очищен\\.")
                            .parseMode("MarkdownV2")
                            .replyMarkup(miniMenu.mainMenu())
                            .disableWebPagePreview(true)
                            .build();
                }

                default -> SendMessage.builder()
                        .chatId(String.valueOf(chatId))
                        .text("Неизвестное действие\\.")
                        .parseMode("MarkdownV2")
                        .disableWebPagePreview(true)
                        .build();
            };
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText().trim();

            var await = stateService.getAwait(chatId);
            if (await != MenuStateService.Await.NONE && !text.startsWith("/")) {
                switch (await) {
                    case ADD_GENRE -> userProfileService.addGenres(chatId, com.gnemirko.movieRecsBot.util.CmdText.parseArgs(text));
                    case ADD_ACTOR -> userProfileService.addActors(chatId, com.gnemirko.movieRecsBot.util.CmdText.parseArgs(text));
                    case ADD_DIRECTOR -> userProfileService.addDirectors(chatId, com.gnemirko.movieRecsBot.util.CmdText.parseArgs(text));
                    case ADD_BLOCK -> userProfileService.blockTags(chatId, com.gnemirko.movieRecsBot.util.CmdText.parseArgs(text));
                }
                stateService.clear(chatId);
                return SendMessage.builder()
                        .chatId(String.valueOf(chatId))
                        .text(profileHandler.profileText(chatId))
                        .parseMode("MarkdownV2")
                        .replyMarkup(miniMenu.mainMenu())
                        .disableWebPagePreview(true)
                        .build();
            }

            if (text.startsWith("/")) {
                if ("/menu".equalsIgnoreCase(text)) {
                    return SendMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .text("Что меняем\\? Выбери действие ниже.")
                            .parseMode("MarkdownV2")
                            .replyMarkup(miniMenu.mainMenu())
                            .disableWebPagePreview(true)
                            .build();
                }

                String reply = profileHandler.handle(chatId, text);
                if (reply != null) {
                    var b = SendMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .text(reply)
                            .parseMode("MarkdownV2")
                            .disableWebPagePreview(true);
                    if ("/profile".equalsIgnoreCase(text)) {
                        b.replyMarkup(miniMenu.mainMenu());
                    }
                    return b.build();
                }

                if ("/start".equalsIgnoreCase(text)) {
                    return SendMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .text("👋 Привет\\! Напиши жанр/настроение или команду /help")
                            .parseMode("MarkdownV2")
                            .disableWebPagePreview(true)
                            .build();
                }
                if ("/recommend".equalsIgnoreCase(text)) {
                    String out = recommendationService.reply(chatId, "дай рекомендации");
                    return SendMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .text(out)
                            .parseMode("MarkdownV2")
                            .disableWebPagePreview(true)
                            .build();
                }
                return SendMessage.builder()
                        .chatId(String.valueOf(chatId))
                        .text("Неизвестная команда\\. /help")
                        .parseMode("MarkdownV2")
                        .disableWebPagePreview(true)
                        .build();
            }

            String out = recommendationService.reply(chatId, text);
            return SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text(out)
                    .parseMode("MarkdownV2")
                    .disableWebPagePreview(true)
                    .build();
        }

        return null;
    }

    private SendMessage prompt(long chatId, String text, MenuStateService.Await state) {
        stateService.setAwait(chatId, state);
        return SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .parseMode("MarkdownV2")
                .replyMarkup(ForceReplyKeyboard.builder().forceReply(true).build())
                .disableWebPagePreview(true)
                .build();
    }
}