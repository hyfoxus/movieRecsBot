package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.handler.MenuStateService;
import com.gnemirko.movieRecsBot.handler.MiniMenu;
import com.gnemirko.movieRecsBot.handler.PromptService;
import com.gnemirko.movieRecsBot.util.CmdText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Service
@RequiredArgsConstructor
public class OpinionService {

    private final UserProfileService userProfileService;
    private final MiniMenu miniMenu;
    private final PromptService promptService;

    public OpinionResult save(long chatId, String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return OpinionResult.failure(promptForOpinion(chatId,
                    "Нужно указать фильм и мнение. Попробуй снова: название — первая строка, мнение — вторая."));
        }

        ParsedOpinion parsed = parse(trimmed);
        if (parsed.title().isBlank()) {
            return OpinionResult.failure(promptForOpinion(chatId,
                    "Не понял название фильма. Напиши его на первой строке, мнение — на второй."));
        }

        userProfileService.addMovieOpinion(chatId, parsed.title(), parsed.opinion());

        StringBuilder text = new StringBuilder("Записал мнение про *")
                .append(CmdText.esc(parsed.title()))
                .append("*.");
        if (!parsed.opinion().isBlank()) {
            text.append("\\nСпасибо, учту это в рекомендациях\\!");
        }

        SendMessage reply = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text.toString())
                .parseMode("MarkdownV2")
                .replyMarkup(miniMenu.mainMenu())
                .disableWebPagePreview(true)
                .build();
        return OpinionResult.success(reply);
    }

    private SendMessage promptForOpinion(long chatId, String message) {
        return promptService.prompt(chatId, message, MenuStateService.Await.ADD_OPINION);
    }

    private ParsedOpinion parse(String trimmed) {
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
        return new ParsedOpinion(title, opinion);
    }

    public record OpinionResult(SendMessage message, boolean success) {
        public static OpinionResult success(SendMessage message) {
            return new OpinionResult(message, true);
        }

        public static OpinionResult failure(SendMessage message) {
            return new OpinionResult(message, false);
        }
    }

    private record ParsedOpinion(String title, String opinion) {}
}
