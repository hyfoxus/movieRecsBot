package com.gnemirko.movieRecsBot.handler;

import com.gnemirko.movieRecsBot.service.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;


@Component
@RequiredArgsConstructor
public class UpdateRouter {

    private final RecommendationService recommendationService;

    public String handle(Update update) {
        String text = null;
        if (update.hasMessage() && update.getMessage().hasText()) {
            text = update.getMessage().getText().trim();
        } else if (update.hasCallbackQuery()) {
            text = update.getCallbackQuery().getData();
        }

        if (text == null || text.isBlank()) return "Пока понимаю только текстовые сообщения 🙂";

        if ("/start".equalsIgnoreCase(text)) {
            return "👋 Привет! Напиши жанр/настроение/пример фильма — подберу 3-5 вариантов с краткими пояснениями.";
        }

        return recommendationService.reply(update.getMessage().getChatId(), text);
    }
}