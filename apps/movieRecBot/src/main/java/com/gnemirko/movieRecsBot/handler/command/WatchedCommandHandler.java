package com.gnemirko.movieRecsBot.handler.command;

import com.gnemirko.movieRecsBot.handler.MenuStateService;
import com.gnemirko.movieRecsBot.handler.PromptService;
import com.gnemirko.movieRecsBot.service.OpinionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;

@Component
@RequiredArgsConstructor
class WatchedCommandHandler implements BotCommandHandler {

    private static final String INSTRUCTIONS = """
            Расскажи: первая строка — фильм, вторая — мнение. Пример:
            Inception
            Очень понравился.
            """;

    private final PromptService promptService;
    private final OpinionService opinionService;
    private final MenuStateService menuStateService;

    @Override
    public boolean supports(String command) {
        return "/watched".equals(command);
    }

    @Override
    public BotApiMethod<?> handle(CommandContext context) {
        String payload = context.argument();
        if (payload == null || payload.isBlank()) {
            return promptService.prompt(context.chatId(), INSTRUCTIONS.trim(), MenuStateService.Await.ADD_OPINION);
        }
        var result = opinionService.save(context.chatId(), payload);
        if (result.success()) {
            menuStateService.clear(context.chatId());
        }
        return result.message();
    }
}
