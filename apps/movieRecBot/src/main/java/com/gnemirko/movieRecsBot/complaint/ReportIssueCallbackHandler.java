package com.gnemirko.movieRecsBot.complaint;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

@Component
@RequiredArgsConstructor
public class ReportIssueCallbackHandler {

    private final ComplaintFlowService complaintFlowService;

    public BotApiMethod<?> handle(CallbackQuery callbackQuery) {
        if (callbackQuery == null) {
            return null;
        }
        return complaintFlowService.startFromCallback(callbackQuery);
    }
}
