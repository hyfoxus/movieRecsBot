package com.gnemirko.movieRecsBot.handler;

import com.gnemirko.movieRecsBot.service.RecommendationService;
import com.gnemirko.movieRecsBot.service.TaskManagerService;
import com.gnemirko.movieRecsBot.service.UserProfileService;
import com.gnemirko.movieRecsBot.util.CmdText;
import com.gnemirko.movieRecsBot.handler.StatusCommands;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ForceReplyKeyboard;

@Component
@RequiredArgsConstructor
public class UpdateRouter {

    private final ProfileCommandHandler profileHandler;
    private final RecommendationService recommendationService;
    private final UserProfileService userProfileService;
    private final MiniMenu miniMenu;
    private final MenuStateService stateService;
    private final TaskManagerService taskManagerService;
    private final StatusCommands statusCommands;

    public BotApiMethod<?> handle(Update update) {
        if (update == null) return null;

        if (update.hasCallbackQuery()) {
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            String data = String.valueOf(update.getCallbackQuery().getData());

            return switch (data) {
                case "menu:show" -> profileMessage(chatId);
                case "menu:add_genre" ->
                        prompt(chatId, "Введи жанры через запятую.", MenuStateService.Await.ADD_GENRE);
                case "menu:add_actor" ->
                        prompt(chatId, "Введи актёров через запятую.", MenuStateService.Await.ADD_ACTOR);
                case "menu:add_director" ->
                        prompt(chatId, "Введи режиссёров через запятую.", MenuStateService.Await.ADD_DIRECTOR);
                case "menu:add_block" ->
                        prompt(chatId, "Введи анти-метки через запятую.", MenuStateService.Await.ADD_BLOCK);
                case "menu:add_opinion" -> prompt(chatId,
                        "Напиши название фильма на первой строке, мнение — на второй. Пример:\nInception\nОчень понравился.",
                        MenuStateService.Await.ADD_OPINION);
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
                    case ADD_GENRE -> {
                        userProfileService.addGenres(chatId, CmdText.parseArgs(text));
                        stateService.clear(chatId);
                        return profileMessage(chatId);
                    }
                    case ADD_ACTOR -> {
                        userProfileService.addActors(chatId, CmdText.parseArgs(text));
                        stateService.clear(chatId);
                        return profileMessage(chatId);
                    }
                    case ADD_DIRECTOR -> {
                        userProfileService.addDirectors(chatId, CmdText.parseArgs(text));
                        stateService.clear(chatId);
                        return profileMessage(chatId);
                    }
                    case ADD_BLOCK -> {
                        userProfileService.blockTags(chatId, CmdText.parseArgs(text));
                        stateService.clear(chatId);
                        return profileMessage(chatId);
                    }
                    case ADD_OPINION -> {
                        OpinionResult result = saveOpinion(chatId, text);
                        if (result.success()) {
                            stateService.clear(chatId);
                        }
                        return result.message();
                    }
                    default -> {
                    }
                }
            }

            if (text.startsWith("/")) {
                String command = text.split("\\s+", 2)[0].toLowerCase();

                if ("/menu".equals(command)) {
                    return SendMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .text("Что меняем? Выбери действие ниже.")
                            .replyMarkup(miniMenu.mainMenu())
                            .disableWebPagePreview(true)
                            .build();
                }

                if ("/profile".equals(command)) {
                    return profileMessage(chatId);
                }

                if ("/help".equals(command)) {
                    return SendMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .text(profileHandler.helpText())
                            .parseMode("MarkdownV2")
                            .disableWebPagePreview(true)
                            .build();
                }

                if ("/start".equals(command)) {
                    return SendMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .text("👋 Привет! Напиши жанр/настроение или команду /help")
                            .disableWebPagePreview(true)
                            .build();
                }

                if ("/status".equals(command)) {
                    String arg = null;
                    if (text.length() > command.length()) {
                        arg = text.substring(command.length()).trim();
                        if (arg.isEmpty()) arg = null;
                    }
                    String ans = statusCommands.statusForChat(chatId, arg);
                    return SendMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .text(ans)
                            .parseMode("MarkdownV2")
                            .disableWebPagePreview(true)
                            .build();
                }

                if ("/recommend".equals(command)) {
                    var task = taskManagerService.enqueue(chatId, null, "дай рекомендации");
                    String displayId = task.getDisplayId();
                    return SendMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .text("✅ Запрос принят\\. Задача №" + displayId + " в очереди\\.\\n" +
                                    "Напиши `/status " + displayId + "` чтобы посмотреть прогресс\\.")
                            .parseMode("MarkdownV2")
                            .replyMarkup(miniMenu.mainMenu())
                            .disableWebPagePreview(true)
                            .build();
                }

                if ("/watched".equals(command)) {
                    String payload = text.length() > command.length()
                            ? text.substring(command.length()).trim()
                            : "";
                    if (payload.isEmpty()) {
                    return prompt(chatId,
                            "Расскажи: первая строка — фильм, вторая — мнение. Пример:\nInception\nОчень понравился.",
                            MenuStateService.Await.ADD_OPINION);
                    }
                    OpinionResult result = saveOpinion(chatId, payload);
                    if (result.success()) {
                        stateService.clear(chatId);
                    }
                    return result.message();
                }

                if ("/like_genre".equals(command)) {
                    return prompt(chatId,
                            "Введи жанры через запятую.",
                            MenuStateService.Await.ADD_GENRE);
                }

                if ("/like_actor".equals(command)) {
                    return prompt(chatId,
                            "Введи актёров через запятую.",
                            MenuStateService.Await.ADD_ACTOR);
                }

                if ("/like_director".equals(command)) {
                    return prompt(chatId,
                            "Введи режиссёров через запятую.",
                            MenuStateService.Await.ADD_DIRECTOR);
                }

                if ("/block".equals(command)) {
                    return prompt(chatId,
                            "Введи анти-метки через запятую.",
                            MenuStateService.Await.ADD_BLOCK);
                }

                if ("/reset_profile".equals(command)) {
                    userProfileService.reset(chatId);
                    stateService.clear(chatId);
                    return SendMessage.builder()
                            .chatId(String.valueOf(chatId))
                            .text("Профиль очищен\\.")
                            .parseMode("MarkdownV2")
                            .replyMarkup(miniMenu.mainMenu())
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

            var task = taskManagerService.enqueue(chatId, null, text);
            String displayId = task.getDisplayId();
            return SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    // uncomment this if you want to see the text about query in chat
//                    .text("✅ Запрос принят\\. Задача №" + displayId + " в очереди\\.\n" +
//                            "Напиши `/status " + displayId + "` чтобы посмотреть прогресс\\.")
//                    .parseMode("MarkdownV2")
//                    .replyMarkup(miniMenu.mainMenu())
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
                .replyMarkup(ForceReplyKeyboard.builder().forceReply(true).build())
                .disableWebPagePreview(true)
                .build();
    }

    private SendMessage profileMessage(long chatId) {
        return SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(profileHandler.profileText(chatId))
                .parseMode("MarkdownV2")
                .replyMarkup(miniMenu.mainMenu())
                .disableWebPagePreview(true)
                .build();
    }

    private OpinionResult saveOpinion(long chatId, String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return new OpinionResult(prompt(chatId,
                    "Нужно указать фильм и мнение. Попробуй снова: название — первая строка, мнение — вторая.",
                    MenuStateService.Await.ADD_OPINION), false);
        }

        String title;
        String opinion;

        String[] byLine = trimmed.split("\\r?\\n", 2);
        if (byLine.length == 2) {
            title = byLine[0].trim();
            opinion = byLine[1].trim();
        } else {
            String[] semicolon = trimmed.split("\\s*;\\s*", 2);
            if (semicolon.length == 2) {
                title = semicolon[0].trim();
                opinion = semicolon[1].trim();
            } else {
                String[] dash = trimmed.split("\\s*[–—-]\\s*", 2);
                if (dash.length == 2) {
                    title = dash[0].trim();
                    opinion = dash[1].trim();
                } else {
                    title = trimmed;
                    opinion = "";
                }
            }
        }

        if (title.isEmpty()) {
            return new OpinionResult(prompt(chatId,
                    "Не понял название фильма. Напиши его на первой строке, мнение — на второй.",
                    MenuStateService.Await.ADD_OPINION), false);
        }

        userProfileService.addMovieOpinion(chatId, title, opinion);
        String text = "Записал мнение про *" + CmdText.esc(title) + "*.";
        if (!opinion.isEmpty()) {
            text += "\\nСпасибо, учту это в рекомендациях\\!";
        }
        return new OpinionResult(SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .parseMode("MarkdownV2")
                .replyMarkup(miniMenu.mainMenu())
                .disableWebPagePreview(true)
                .build(), true);
    }

    private record OpinionResult(SendMessage message, boolean success) {}
}
