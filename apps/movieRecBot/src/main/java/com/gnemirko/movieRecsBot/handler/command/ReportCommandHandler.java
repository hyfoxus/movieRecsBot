package com.gnemirko.movieRecsBot.handler.command;

import com.gnemirko.movieRecsBot.complaint.ComplaintFlowService;
import com.gnemirko.movieRecsBot.complaint.ReportTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
@RequiredArgsConstructor
public class ReportCommandHandler implements BotCommandHandler {

    private final ComplaintFlowService complaintFlowService;

    @Override
    public boolean supports(String command) {
        return "/report".equals(command);
    }

    @Override
    public BotApiMethod<?> handle(CommandContext context) {
        Update update = context.update();
        if (update == null || update.getMessage() == null) {
            return SendMessage.builder()
                    .chatId(String.valueOf(context.chatId()))
                    .text("Не могу обработать жалобу, попробуй ещё раз.")
                    .disableWebPagePreview(true)
                    .build();
        }
        Message message = update.getMessage();
        ReportTarget target = ReportTarget.fromMessage(message.getReplyToMessage());
        String argument = context.argument();
        if (argument == null || argument.isBlank()) {
            return complaintFlowService.startFromCommand(message, target);
        }
        return complaintFlowService.submitImmediately(message, target, argument);
    }
}
