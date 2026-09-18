package com.gnemirko.movieRecsBot.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramConfigTest {

    private TelegramConfig configWith(String botToken, String webhookUrl, String webhookPath, String webhookSecret) {
        TelegramConfig config = new TelegramConfig();
        ReflectionTestUtils.setField(config, "botToken", botToken);
        ReflectionTestUtils.setField(config, "webhookUrl", webhookUrl);
        ReflectionTestUtils.setField(config, "webhookPath", webhookPath);
        ReflectionTestUtils.setField(config, "webhookSecret", webhookSecret);
        return config;
    }

    @Test
    void buildSetWebhookIncludesTheConfiguredSecretToken() {
        TelegramConfig config = configWith("bot-token", "https://example.com", "/tg/webhook", "super-secret");

        SetWebhook setWebhook = config.buildSetWebhook("https://example.com/tg/webhook");

        assertThat(setWebhook.getUrl()).isEqualTo("https://example.com/tg/webhook");
        assertThat(setWebhook.getSecretToken()).isEqualTo("super-secret");
    }

    @Test
    void initFailsFastWhenBotTokenIsMissing() {
        TelegramConfig config = configWith("", "https://example.com", "/tg/webhook", "super-secret");

        assertThatThrownBy(config::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token");
    }

    @Test
    void initFailsFastWhenWebhookSecretIsMissing() {
        TelegramConfig config = configWith("bot-token", "https://example.com", "/tg/webhook", "");

        assertThatThrownBy(config::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secret");
    }

    @Test
    void initFailsFastWhenWebhookUrlIsMissing() {
        TelegramConfig config = configWith("bot-token", "", "/tg/webhook", "super-secret");

        assertThatThrownBy(config::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("URL");
    }
}
