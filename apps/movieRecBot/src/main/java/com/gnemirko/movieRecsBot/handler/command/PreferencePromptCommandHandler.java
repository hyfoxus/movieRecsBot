package com.gnemirko.movieRecsBot.handler.command;

import com.gnemirko.movieRecsBot.handler.MenuStateService;
import com.gnemirko.movieRecsBot.handler.PromptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;

import java.util.Map;

@Component
@RequiredArgsConstructor
class PreferencePromptCommandHandler implements BotCommandHandler {

    private static final Map<String, Prompt> PROMPTS = Map.of(
            "/like_genre", new Prompt("Введи жанры через запятую.", MenuStateService.Await.ADD_GENRE),
            "/like_actor", new Prompt("Введи актёров через запятую.", MenuStateService.Await.ADD_ACTOR),
            "/like_director", new Prompt("Введи режиссёров через запятую.", MenuStateService.Await.ADD_DIRECTOR),
            "/block", new Prompt("Введи анти-метки через запятую.", MenuStateService.Await.ADD_BLOCK)
    );

    private final PromptService promptService;

    @Override
    public boolean supports(String command) {
        return PROMPTS.containsKey(command);
    }

    @Override
    public BotApiMethod<?> handle(CommandContext context) {
        Prompt prompt = PROMPTS.get(context.command());
        return promptService.prompt(context.chatId(), prompt.text(), prompt.state());
    }

    private record Prompt(String text, MenuStateService.Await state) {}
}
