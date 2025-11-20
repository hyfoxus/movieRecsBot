package com.gnemirko.movieRecsBot.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ForceReplyKeyboard;

@Component
@RequiredArgsConstructor
public class PromptService {

    private final MenuStateService menuStateService;

    public SendMessage prompt(long chatId, String text, MenuStateService.Await state) {
        menuStateService.setAwait(chatId, state);
        return SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .replyMarkup(ForceReplyKeyboard.builder().forceReply(true).build())
                .disableWebPagePreview(true)
                .build();
    }
}
