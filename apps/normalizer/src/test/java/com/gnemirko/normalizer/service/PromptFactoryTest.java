package com.gnemirko.normalizer.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptFactoryTest {

    @Test
    void languageDetectionPromptEmbedsTextBetweenDelimiters() {
        String prompt = PromptFactory.languageDetectionPrompt("Привет мир");

        assertThat(prompt).contains("<<<Привет мир>>>");
        assertThat(prompt).contains("valid JSON only");
        assertThat(prompt).contains("\"language\":\"<iso-639-1>\"");
    }

    @Test
    void languageDetectionPromptTreatsNullTextAsEmpty() {
        String prompt = PromptFactory.languageDetectionPrompt(null);

        assertThat(prompt).contains("<<<>>>");
    }

    @Test
    void languageDetectionPromptPreservesSpecialCharacters() {
        String text = "50% off <b>deal</b> & \"quotes\"";

        String prompt = PromptFactory.languageDetectionPrompt(text);

        assertThat(prompt).contains("<<<" + text + ">>>");
    }

    @Test
    void translationPromptEmbedsSourceTargetAndText() {
        String prompt = PromptFactory.translationPrompt("Фильм на вечер", "ru", "en");

        assertThat(prompt).contains("Input language: ru");
        assertThat(prompt).contains("Output language: en");
        assertThat(prompt).contains("<<<Фильм на вечер>>>");
        assertThat(prompt).contains("Preserve movie titles, names, and entities.");
    }

    @Test
    void translationPromptTreatsNullTextAsEmpty() {
        String prompt = PromptFactory.translationPrompt(null, "ru", "en");

        assertThat(prompt).contains("<<<>>>");
    }

    @Test
    void translationPromptHandlesNullLanguageCodesLiterally() {
        String prompt = PromptFactory.translationPrompt("hi", null, null);

        assertThat(prompt).contains("Input language: null");
        assertThat(prompt).contains("Output language: null");
    }
}
