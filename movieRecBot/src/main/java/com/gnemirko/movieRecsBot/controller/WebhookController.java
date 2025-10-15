package com.gnemirko.movieRecsBot.controller;

import com.gnemirko.movieRecsBot.handler.UpdateRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final UpdateRouter router;

    @PostMapping(path = {"${telegram.bot.webhook-path}", "${telegram.bot.webhook-path}/"})
    public BotApiMethod<?> onUpdate(@RequestBody Update update) {
        try {
            if (update == null) {
                log.warn("Received null update");
                return null;
            }

            log.debug("Received update id={}, hasMessage={}, hasCallbackQuery={}",
                    update.getUpdateId(), update.hasMessage(), update.hasCallbackQuery());

            if (update.hasMessage() && update.getMessage().hasText()) {
                log.debug("Message from user {}: {}",
                        update.getMessage().getFrom() != null ? update.getMessage().getFrom().getId() : "unknown",
                        update.getMessage().getText());
            }

            BotApiMethod<?> response = router.handle(update);
            if (response == null) {
                log.debug("No response to send for update id={}", update.getUpdateId());
            } else {
                log.debug("Prepared response for update id={}", update.getUpdateId());
            }
            return response;
        } catch (Exception e) {
            log.error("Webhook handling error", e);
            return null;
        }
    }

    @GetMapping({"${telegram.bot.webhook-path}", "${telegram.bot.webhook-path}/"})
    public String health() {
        log.debug("Webhook health check");
        return "OK";
    }
}