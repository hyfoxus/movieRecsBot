package com.gnemirko.movieRecsBot.controller;

import com.gnemirko.movieRecsBot.webhook.MovieWebhookBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final MovieWebhookBot bot;                 // твой бот
    private final com.gnemirko.movieRecsBot.handler.UpdateRouter router;

    @PostMapping(path = "${telegram.bot.webhook-path}", consumes = "application/json")
    public ResponseEntity<String> onUpdate(@RequestBody Update update) {
        try {
            Long chatId = null;
            if (update.hasMessage() && update.getMessage().hasText()) {
                chatId = update.getMessage().getChatId();
            } else if (update.hasCallbackQuery()) {
                chatId = update.getCallbackQuery().getMessage().getChatId();
            }

            if (chatId != null) {
                String reply = router.handle(update); // твоя логика /start и т.п.
                bot.execute(SendMessage.builder()
                        .chatId(String.valueOf(chatId))
                        .text(reply)
                        .disableWebPagePreview(true)
                        .build());
            } else {
                log.debug("Skip update without chatId: {}", update);
            }
        } catch (Exception e) {
            log.error("Webhook handling error", e);
            // Важно: всё равно возвращаем 200, чтобы Telegram не считал это ошибкой
        }
        return ResponseEntity.ok("OK");
    }

    @GetMapping("${telegram.bot.webhook-path}")
    public String health() {
        return "OK";
    }
}