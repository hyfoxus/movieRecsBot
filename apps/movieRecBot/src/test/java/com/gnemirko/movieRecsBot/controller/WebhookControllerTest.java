package com.gnemirko.movieRecsBot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.movieRecsBot.complaint.ReportButtonDecorator;
import com.gnemirko.movieRecsBot.handler.UpdateRouter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
@TestPropertySource(properties = "telegram.bot.webhook-path=/tg/webhook")
@AutoConfigureMockMvc(addFilters = false)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UpdateRouter updateRouter;

    @Autowired
    private ReportButtonDecorator reportButtonDecorator;

    @Test
    void postUpdateDelegatesToRouter() throws Exception {
        Update update = new Update();
        update.setUpdateId(15);
        Message message = new Message();
        message.setMessageId(5);
        message.setText("Фильм на вечер");
        Chat chat = new Chat();
        chat.setId(777L);
        message.setChat(chat);
        update.setMessage(message);

        SendMessage sendMessage = SendMessage.builder()
                .chatId("777")
                .text("ok")
                .build();
        when(updateRouter.handle(any(Update.class))).thenAnswer(invocation -> sendMessage);

        mockMvc.perform(post("/tg/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        verify(updateRouter).handle(any(Update.class));
        verify(reportButtonDecorator).decorate(sendMessage);
    }

    @Test
    void getHealthReturnsOk() throws Exception {
        mockMvc.perform(get("/tg/webhook"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @TestConfiguration
    static class MockConfig {
        @Bean
        UpdateRouter updateRouter() {
            return Mockito.mock(UpdateRouter.class);
        }

        @Bean
        ReportButtonDecorator reportButtonDecorator() {
            return Mockito.mock(ReportButtonDecorator.class);
        }
    }
}
