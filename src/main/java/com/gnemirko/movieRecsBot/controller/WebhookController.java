package com.gnemirko.movieRecsBot.controller;

import com.gnemirko.movieRecsBot.webhook.MovieWebhookBot;
import com.gnemirko.movieRecsBot.handler.UpdateRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.Update;

@Slf4j
@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final MovieWebhookBot bot;   // keep if needed elsewhere
    private final UpdateRouter router;

    @PostMapping(path = "${telegram.bot.webhook-path}",
            consumes = "application/json",
            produces = "application/json")
    public ResponseEntity<?> onUpdate(@RequestBody Update update) {
        try {
            BotApiMethod<?> response = router.handle(update); // router must build SendMessage/EditMessage*
            return response != null ? ResponseEntity.ok(response) : ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Webhook handling error", e);
            return ResponseEntity.ok().build();
        }
    }

    @GetMapping("${telegram.bot.webhook-path}")
    public String health() {
        return "OK";
    }
}