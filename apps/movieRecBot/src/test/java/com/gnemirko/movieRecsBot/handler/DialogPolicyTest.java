package com.gnemirko.movieRecsBot.handler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class DialogPolicyTest {

    private Locale originalLocale;

    @BeforeEach
    void captureLocale() {
        originalLocale = Locale.getDefault();
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void recommendNowIgnoresDefaultLocaleWhenNormalizing() {
        Locale.setDefault(new Locale("tr"));

        DialogPolicy policy = new DialogPolicy();

        assertThat(policy.recommendNow(17L, "Give Recommendation please")).isTrue();
    }

    @Test
    void recommendNowTriggersAfterClarifyingLimit() {
        DialogPolicy policy = new DialogPolicy();
        long chatId = 42L;

        policy.countClarifying(chatId);
        policy.countClarifying(chatId);

        assertThat(policy.recommendNow(chatId, "Need more ideas")).isTrue();
    }
}
