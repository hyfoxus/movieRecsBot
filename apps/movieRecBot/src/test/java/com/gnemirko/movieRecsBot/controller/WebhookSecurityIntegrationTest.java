package com.gnemirko.movieRecsBot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.movieRecsBot.complaint.ReportButtonDecorator;
import com.gnemirko.movieRecsBot.config.SecurityConfig;
import com.gnemirko.movieRecsBot.config.TelegramWebhookProperties;
import com.gnemirko.movieRecsBot.handler.UpdateRouter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the webhook endpoint with the real security filter chain enabled (unlike
 * {@link WebhookControllerTest}, which disables filters to test routing logic in isolation) to
 * confirm the webhook-secret check actually blocks unauthenticated POSTs at the HTTP layer.
 */
@WebMvcTest(WebhookController.class)
@Import({SecurityConfig.class, TelegramWebhookProperties.class})
@TestPropertySource(properties = {
        "telegram.bot.webhook-path=/tg/webhook",
        "telegram.bot.webhook-secret=test-secret-value"
})
class WebhookSecurityIntegrationTest {

    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UpdateRouter updateRouter;

    @Test
    void rejectsWebhookPostMissingSecretHeader() throws Exception {
        mockMvc.perform(post("/tg/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(updateRouter);
    }

    @Test
    void rejectsWebhookPostWithWrongSecretHeader() throws Exception {
        mockMvc.perform(post("/tg/webhook")
                        .header(SECRET_HEADER, "not-the-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(updateRouter);
    }

    @Test
    void acceptsWebhookPostWithCorrectSecretHeader() throws Exception {
        Update update = new Update();
        update.setUpdateId(42);
        when(updateRouter.handle(any(Update.class))).thenReturn(null);

        mockMvc.perform(post("/tg/webhook")
                        .header(SECRET_HEADER, "test-secret-value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());

        verify(updateRouter).handle(any(Update.class));
    }

    @Test
    void healthCheckStaysAccessibleWithoutSecretHeader() throws Exception {
        mockMvc.perform(get("/tg/webhook"))
                .andExpect(status().isOk());
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
