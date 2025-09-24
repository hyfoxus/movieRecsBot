package com.gnemirko.movieRecsBot.handler;

import com.gnemirko.movieRecsBot.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class UpdateRouter {

    private final ProfileCommandHandler profileHandler;
    private final RecommendationService recommendationService;

    public String handle(Update update) {
        if (update == null || !update.hasMessage() || !update.getMessage().hasText()) return null;
        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        if (text.startsWith("/")) {
            String reply = profileHandler.handle(chatId, text);
            if (reply != null) return reply;
            if ("/start".equalsIgnoreCase(text)) return "👋 Привет\\! Напиши жанр/настроение или команду /help";
            if ("/recommend".equalsIgnoreCase(text)) return recommendationService.reply(chatId, "дай рекомендации");
            return "Неизвестная команда\\. /help";
        }

        return recommendationService.reply(chatId, text);
    }
}