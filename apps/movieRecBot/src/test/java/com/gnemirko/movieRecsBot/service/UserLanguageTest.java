package com.gnemirko.movieRecsBot.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserLanguageTest {

    @Test
    void detectsRussianAndProvidesDirective() {
        UserLanguage lang = UserLanguage.detect("Подбери что-то наподобие Матрицы");
        assertThat(lang.isoCode()).isEqualToIgnoringCase("RU");
        assertThat(lang.directive("Подбери что-то"))
                .contains("Russian")
                .contains("Respond strictly in that language");
    }

    @Test
    void detectsEnglish() {
        UserLanguage lang = UserLanguage.detect("Recommend a fun movie");
        assertThat(lang.requiresTranslation()).isFalse();
    }

    @Test
    void detectsSerbian() {
        UserLanguage lang = UserLanguage.detect("Preporuči mi neki film");
        assertThat(lang.isoCode()).isNotBlank();
        assertThat(lang.isoCode().equalsIgnoreCase("EN")).isFalse();
        assertThat(lang.requiresTranslation()).isTrue();
    }

    @Test
    void defaultsToEnglishWhenUnknown() {
        UserLanguage lang = UserLanguage.detect("123456 !!!");
        assertThat(lang.isoCode()).isEqualToIgnoringCase("EN");
        assertThat(lang.directive(""))
                .contains("Respond in English");
    }
}
