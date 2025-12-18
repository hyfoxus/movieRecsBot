package com.gnemirko.movieRecsBot.complaint;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;

public record ReportSession(long chatId, Long userId, String userLabel, ReportTarget target) {

    public static ReportSession fromCallback(CallbackQuery callbackQuery) {
        MaybeInaccessibleMessage maybeMessage = callbackQuery.getMessage();
        User user = callbackQuery.getFrom();
        Long chatId = maybeMessage.getChatId();
        if (chatId == null && user != null) {
            chatId = user.getId();
        }
        if (chatId == null) {
            throw new IllegalStateException("Cannot resolve chatId for callback query");
        }
        return new ReportSession(
                chatId,
                user != null ? user.getId() : null,
                ComplaintTextUtil.describeUser(user),
                ReportTarget.fromMessage(maybeMessage)
        );
    }

    public static ReportSession fromCommandMessage(Message commandMessage, ReportTarget target) {
        User user = commandMessage.getFrom();
        return new ReportSession(
                commandMessage.getChatId(),
                user != null ? user.getId() : null,
                ComplaintTextUtil.describeUser(user),
                target == null ? ReportTarget.empty() : target
        );
    }
}
