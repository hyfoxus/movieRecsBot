package com.gnemirko.movieRecsBot.complaint;

import com.gnemirko.movieRecsBot.handler.MenuStateService;
import com.gnemirko.movieRecsBot.handler.PromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintFlowService {

    private static final String PROMPT_MESSAGE = "Опиши проблему одним или несколькими предложениями. " +
            "Мы рассмотрим жалобу и поправим бота.";
    private static final String EMPTY_REMINDER = "Нужно описать проблему текстом. Напиши, что именно пошло не так.";
    private static final String THANK_YOU = "Спасибо! Жалоба записана.";

    private final PromptService promptService;
    private final MenuStateService menuStateService;
    private final ReportSessionStore sessionStore;
    private final ComplaintLogger complaintLogger;

    public SendMessage startFromCallback(CallbackQuery callbackQuery) {
        ReportSession session = ReportSession.fromCallback(callbackQuery);
        sessionStore.save(session);
        return promptForComplaint(session.chatId(), PROMPT_MESSAGE);
    }

    public SendMessage startFromCommand(Message commandMessage, ReportTarget target) {
        ReportSession session = ReportSession.fromCommandMessage(commandMessage, target);
        sessionStore.save(session);
        return promptForComplaint(session.chatId(), PROMPT_MESSAGE);
    }

    public SendMessage submitImmediately(Message commandMessage, ReportTarget target, String complaintText) {
        String trimmed = trim(complaintText);
        if (!StringUtils.hasText(trimmed)) {
            return startFromCommand(commandMessage, target);
        }
        ReportSession session = ReportSession.fromCommandMessage(commandMessage, target);
        logComplaint(session, trimmed);
        return confirmation(session.chatId());
    }

    public SendMessage handleAwaitedComplaint(long chatId, String complaintText) {
        String trimmed = trim(complaintText);
        if (!StringUtils.hasText(trimmed)) {
            return promptForComplaint(chatId, EMPTY_REMINDER);
        }
        ReportSession session = sessionStore.get(chatId)
                .orElseGet(() -> {
                    log.warn("No report session found for chat {}. Using fallback context.", chatId);
                    return new ReportSession(chatId, null, "chat " + chatId, ReportTarget.empty());
                });
        logComplaint(session, trimmed);
        return confirmation(chatId);
    }

    private void logComplaint(ReportSession session, String complaintText) {
        ComplaintRecord record = ComplaintRecord.of(
                session.chatId(),
                session.userId(),
                session.userLabel(),
                complaintText,
                session.target().describe()
        );
        complaintLogger.log(record);
        sessionStore.clear(session.chatId());
        menuStateService.clear(session.chatId());
    }

    private SendMessage promptForComplaint(long chatId, String text) {
        return promptService.prompt(chatId, text, MenuStateService.Await.REPORT_COMPLAINT);
    }

    private SendMessage confirmation(long chatId) {
        menuStateService.clear(chatId);
        sessionStore.clear(chatId);
        return SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(THANK_YOU)
                .disableWebPagePreview(true)
                .build();
    }

    private static String trim(String text) {
        return text == null ? "" : text.trim();
    }
}
