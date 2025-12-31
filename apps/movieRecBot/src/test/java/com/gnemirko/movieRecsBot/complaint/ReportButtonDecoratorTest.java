package com.gnemirko.movieRecsBot.complaint;

import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportButtonDecoratorTest {

    private final ReportButtonDecorator enabledDecorator = new ReportButtonDecorator(true);
    private final ReportButtonDecorator disabledDecorator = new ReportButtonDecorator(false);

    @Test
    void addsButtonWhenMarkupMissing() {
        SendMessage message = SendMessage.builder()
                .chatId("1")
                .text("hello")
                .build();

        enabledDecorator.decorate(message);

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) message.getReplyMarkup();
        assertEquals(1, markup.getKeyboard().size());
        InlineKeyboardButton button = markup.getKeyboard().get(0).get(0);
        assertEquals(ReportButtonDecorator.REPORT_CALLBACK_DATA, button.getCallbackData());
    }

    @Test
    void appendsButtonToExistingKeyboard() {
        InlineKeyboardMarkup existing = InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        List.of(InlineKeyboardButton.builder().text("A").callbackData("a").build())
                ))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId("1")
                .text("hello")
                .replyMarkup(existing)
                .build();

        enabledDecorator.decorate(message);

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) message.getReplyMarkup();
        assertEquals(2, markup.getKeyboard().size());
        InlineKeyboardButton button = markup.getKeyboard().get(1).get(0);
        assertEquals(ReportButtonDecorator.REPORT_CALLBACK_DATA, button.getCallbackData());
    }

    @Test
    void doesNotDuplicateButton() {
        InlineKeyboardMarkup existing = InlineKeyboardMarkup.builder()
                .keyboard(List.of(
                        List.of(InlineKeyboardButton.builder()
                                .text("⚠️ Сообщить о проблеме")
                                .callbackData(ReportButtonDecorator.REPORT_CALLBACK_DATA)
                                .build())
                ))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId("1")
                .text("hello")
                .replyMarkup(existing)
                .build();

        enabledDecorator.decorate(message);

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) message.getReplyMarkup();
        assertEquals(1, markup.getKeyboard().size());
        assertTrue(markup.getKeyboard().get(0).stream()
                .allMatch(btn -> ReportButtonDecorator.REPORT_CALLBACK_DATA.equals(btn.getCallbackData())));
    }

    @Test
    void noopWhenDisabled() {
        SendMessage message = SendMessage.builder()
                .chatId("1")
                .text("hello")
                .build();

        disabledDecorator.decorate(message);

        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) message.getReplyMarkup();
        assertNull(markup);
    }
}
