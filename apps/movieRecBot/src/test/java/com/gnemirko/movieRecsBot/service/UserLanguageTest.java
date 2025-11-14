package com.gnemirko.movieRecsBot.service;

import com.github.pemistahl.lingua.api.Language;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserLanguageTest {

    @Test
    void buildsFromRussianLanguage() {
        UserLanguage lang = UserLanguage.fromLanguage(Language.RUSSIAN);
        assertThat(lang.isoCode()).isEqualToIgnoringCase("RU");
        assertThat(lang.directive(""))
                .contains("Russian")
                .contains("Respond strictly in that language");
    }

    @Test
    void englishDoesNotRequireTranslation() {
        UserLanguage lang = UserLanguage.fromLanguage(Language.ENGLISH);
        assertThat(lang.requiresTranslation()).isFalse();
    }

    @Test
    void buildsFromIsoCode() {
        UserLanguage lang = UserLanguage.fromIsoCode("sr");
        assertThat(lang.isoCode()).isEqualTo("sr");
        assertThat(lang.requiresTranslation()).isTrue();
    }

    @Test
    void englishFallback() {
        UserLanguage lang = UserLanguage.englishFallback();
        assertThat(lang.isoCode()).isEqualTo("en");
        assertThat(lang.directive(""))
                .contains("Respond in English");
    }
}
