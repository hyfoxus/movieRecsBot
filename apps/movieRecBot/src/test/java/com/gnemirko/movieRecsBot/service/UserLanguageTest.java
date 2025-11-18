package com.gnemirko.movieRecsBot.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserLanguageTest {

    @Test
    void buildsFromIsoCodeWithDisplayName() {
        UserLanguage lang = UserLanguage.fromIsoCode("ru", "Russian");
        assertThat(lang.isoCode()).isEqualToIgnoringCase("ru");
        assertThat(lang.directive(""))
                .contains("Russian")
                .contains("Respond strictly in that language");
    }

    @Test
    void englishDoesNotRequireTranslation() {
        UserLanguage lang = UserLanguage.fromIsoCode("en");
        assertThat(lang.requiresTranslation()).isFalse();
    }

    @Test
    void englishFallback() {
        UserLanguage lang = UserLanguage.englishFallback();
        assertThat(lang.isoCode()).isEqualTo("en");
        assertThat(lang.directive(""))
                .contains("Respond in English");
    }
}
