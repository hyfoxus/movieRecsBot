package com.gnemirko.movieRecsBot.complaint;

import com.gnemirko.movieRecsBot.handler.MenuStateService;
import com.gnemirko.movieRecsBot.handler.PromptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplaintFlowServiceTest {

    @Mock
    private PromptService promptService;
    @Mock
    private MenuStateService menuStateService;
    @Mock
    private ComplaintLogger complaintLogger;

    private ReportSessionStore sessionStore;
    private ComplaintFlowService service;

    @BeforeEach
    void setUp() {
        sessionStore = new ReportSessionStore();
        service = new ComplaintFlowService(promptService, menuStateService, sessionStore, complaintLogger);
    }

    @Test
    void repromptsWhenTextIsEmpty() {
        SendMessage expected = SendMessage.builder().chatId("77").text("retry").build();
        when(promptService.prompt(eq(77L), anyString(), eq(MenuStateService.Await.REPORT_COMPLAINT))).thenReturn(expected);

        SendMessage response = service.handleAwaitedComplaint(77L, "   ");

        assertEquals(expected, response);
        verify(complaintLogger, never()).log(any());
    }

    @Test
    void logsComplaintWhenSessionAvailable() {
        ReportTarget target = new ReportTarget(5, "bot", "original message");
        ReportSession session = new ReportSession(100L, 200L, "user", target);
        sessionStore.save(session);

        SendMessage response = service.handleAwaitedComplaint(100L, "  Something broke ");

        assertEquals("Спасибо! Жалоба записана.", response.getText());

        ArgumentCaptor<ComplaintRecord> captor = ArgumentCaptor.forClass(ComplaintRecord.class);
        verify(complaintLogger).log(captor.capture());
        ComplaintRecord record = captor.getValue();
        assertEquals(100L, record.chatId());
        assertEquals("Something broke", record.complaintText());
        assertEquals("original message", record.targetDescription());
    }
}
