package com.gnemirko.movieRecsBot.handler.command;

import com.gnemirko.movieRecsBot.handler.MiniMenu;
import com.gnemirko.movieRecsBot.service.TaskManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

@Component
@RequiredArgsConstructor
class RecommendCommandHandler implements BotCommandHandler {

    private final TaskManagerService taskManagerService;
    private final MiniMenu miniMenu;

    @Override
    public boolean supports(String command) {
        return "/recommend".equals(command);
    }

    @Override
    public SendMessage handle(CommandContext context) {
        var task = taskManagerService.enqueue(context.chatId(), null, "дай рекомендации");
        String displayId = task.getDisplayId();
        String text = "✅ Запрос принят\\. Задача №" + displayId + " в очереди\\.\\n" +
                "Напиши `/status " + displayId + "` чтобы посмотреть прогресс\\.";
        return SendMessage.builder()
                .chatId(String.valueOf(context.chatId()))
                .text(text)
                .parseMode("MarkdownV2")
                .replyMarkup(miniMenu.mainMenu())
                .disableWebPagePreview(true)
                .build();
    }
}
