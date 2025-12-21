package com.gnemirko.movieRecsBot.complaint;

import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.objects.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;

public record ReportTarget(Integer messageId, String authorLabel, String text) {

    private static final int MAX_LEN = 500;

    public static ReportTarget empty() {
        return new ReportTarget(null, null, "");
    }

    public static ReportTarget fromMessage(MaybeInaccessibleMessage message) {
        if (message == null) {
            return empty();
        }
        Integer messageId = message.getMessageId();
        String text = "";
        String author = null;
        if (message instanceof Message typed) {
            String rawText = typed.hasText() ? typed.getText() : typed.getCaption();
            text = truncate(rawText);
            User from = typed.getFrom();
            author = from == null ? null : ComplaintTextUtil.describeUser(from);
        }
        return new ReportTarget(messageId, author, text);
    }

    public boolean hasReference() {
        return StringUtils.hasText(text) || messageId != null;
    }

    public String describe() {
        if (StringUtils.hasText(text)) {
            return text;
        }
        if (messageId != null) {
            return "message #" + messageId + (authorLabel != null ? " by " + authorLabel : "");
        }
        return "";
    }

    private static String truncate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String trimmed = raw.trim();
        return trimmed.length() > MAX_LEN ? trimmed.substring(0, MAX_LEN) : trimmed;
    }
}
