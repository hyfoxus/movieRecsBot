package com.gnemirko.movieRecsBot.handler;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

@Component
public class MiniMenu {
    public InlineKeyboardMarkup mainMenu() {
        InlineKeyboardButton show = InlineKeyboardButton.builder().text("📋 Профиль").callbackData("menu:show").build();
        InlineKeyboardButton addGenres = InlineKeyboardButton.builder().text("➕ Жанры").callbackData("menu:add_genre").build();
        InlineKeyboardButton addActors = InlineKeyboardButton.builder().text("➕ Актёры").callbackData("menu:add_actor").build();
        InlineKeyboardButton addDirectors = InlineKeyboardButton.builder().text("➕ Режиссёры").callbackData("menu:add_director").build();
        InlineKeyboardButton addBlock = InlineKeyboardButton.builder().text("⛔ Анти").callbackData("menu:add_block").build();
        InlineKeyboardButton addOpinion = InlineKeyboardButton.builder().text("💬 Отзыв").callbackData("menu:add_opinion").build();
        InlineKeyboardButton reset = InlineKeyboardButton.builder().text("🧹 Очистить").callbackData("menu:reset").build();

        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(show),
                List.of(addGenres, addActors),
                List.of(addDirectors, addBlock),
                List.of(addOpinion),
                List.of(reset)
        );
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }
}
