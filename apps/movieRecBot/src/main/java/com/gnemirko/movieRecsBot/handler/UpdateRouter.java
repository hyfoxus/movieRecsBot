package com.gnemirko.movieRecsBot.handler;

import com.gnemirko.movieRecsBot.complaint.ReportButtonDecorator;
import com.gnemirko.movieRecsBot.complaint.ReportIssueCallbackHandler;
import com.gnemirko.movieRecsBot.handler.command.CommandContext;
import com.gnemirko.movieRecsBot.handler.command.CommandDispatcher;
import com.gnemirko.movieRecsBot.service.ChatActionNotifier;
import com.gnemirko.movieRecsBot.service.TaskManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class UpdateRouter {

    private final MiniMenuCallbackHandler miniMenuCallbackHandler;
    private final ReportIssueCallbackHandler reportIssueCallbackHandler;
    private final MovieFeedbackCallbackHandler movieFeedbackCallbackHandler;
    private final MoreRecommendationsCallbackHandler moreRecommendationsCallbackHandler;
    private final AwaitingReplyHandler awaitingReplyHandler;
    private final CommandDispatcher commandDispatcher;
    private final TaskManagerService taskManagerService;
    private final ChatActionNotifier chatActionNotifier;

    public BotApiMethod<?> handle(Update update) {
        if (update == null) return null;

        if (update.hasCallbackQuery()) {
            var callbackQuery = update.getCallbackQuery();
            String data = callbackQuery.getData();
            if (ReportButtonDecorator.REPORT_CALLBACK_DATA.equals(data)) {
                return reportIssueCallbackHandler.handle(callbackQuery);
            }
            if (data != null && data.startsWith("rate:")) {
                return movieFeedbackCallbackHandler.handle(callbackQuery);
            }
            if ("more:go".equals(data)) {
                return moreRecommendationsCallbackHandler.handle(callbackQuery);
            }
            return miniMenuCallbackHandler.handle(callbackQuery);
        }

        if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText().trim();

            var awaiting = awaitingReplyHandler.handle(chatId, text);
            if (awaiting.isPresent()) {
                return awaiting.get();
            }

            if (text.startsWith("/")) {
                String command = text.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
                CommandContext context = new CommandContext(chatId, command, text, update);
                return commandDispatcher.dispatch(context);
            }

            chatActionNotifier.typing(chatId);
            var task = taskManagerService.enqueue(chatId, null, text);
            String displayId = task.getDisplayId();
            return SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text("Дай мне пару минут!")
                    .disableWebPagePreview(true)
                    .build();
        }
        return null;
    }
}
