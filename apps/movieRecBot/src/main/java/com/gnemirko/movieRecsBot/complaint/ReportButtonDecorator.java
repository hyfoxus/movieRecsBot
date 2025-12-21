package com.gnemirko.movieRecsBot.complaint;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Ensures every outgoing SendMessage carries the report button, preserving existing keyboards.
 */
@Component
public class ReportButtonDecorator {

    public static final String REPORT_CALLBACK_DATA = "report:start";
    private static final String REPORT_BUTTON_TEXT = "⚠️ Сообщить о проблеме";

    public SendMessage decorate(SendMessage message) {
        if (message == null) {
            return null;
        }

        if (message.getReplyMarkup() instanceof InlineKeyboardMarkup inlineKeyboardMarkup) {
            if (hasReportButton(inlineKeyboardMarkup)) {
                return message;
            }
            InlineKeyboardMarkup updated = InlineKeyboardMarkup.builder()
                    .keyboard(appendReportRow(inlineKeyboardMarkup.getKeyboard()))
                    .build();
            message.setReplyMarkup(updated);
            return message;
        }

        message.setReplyMarkup(InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(reportButton())))
                .build());
        return message;
    }

    private List<List<InlineKeyboardButton>> appendReportRow(List<List<InlineKeyboardButton>> keyboard) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        if (keyboard != null) {
            rows.addAll(keyboard);
        }
        rows.add(List.of(reportButton()));
        return rows;
    }

    private boolean hasReportButton(InlineKeyboardMarkup markup) {
        List<List<InlineKeyboardButton>> keyboard = markup.getKeyboard();
        if (keyboard == null) {
            return false;
        }
        for (List<InlineKeyboardButton> row : keyboard) {
            if (row == null) {
                continue;
            }
            for (InlineKeyboardButton button : row) {
                if (button != null && REPORT_CALLBACK_DATA.equals(button.getCallbackData())) {
                    return true;
                }
            }
        }
        return false;
    }

    private InlineKeyboardButton reportButton() {
        return InlineKeyboardButton.builder()
                .text(REPORT_BUTTON_TEXT)
                .callbackData(REPORT_CALLBACK_DATA)
                .build();
    }
}
