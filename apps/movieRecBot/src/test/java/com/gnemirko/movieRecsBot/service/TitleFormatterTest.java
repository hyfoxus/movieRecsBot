package com.gnemirko.movieRecsBot.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TitleFormatterTest {

    @Test
    void overridesInlineYearWithVerifiedValue() {
        String formatted = TitleFormatter.formatWithVerifiedYear("Властелин колец: Братство кольца (2024)", 2001);
        assertThat(formatted).isEqualTo("Властелин колец: Братство кольца (2001)");
    }

    @Test
    void leavesTitleAsIsWhenYearUnknown() {
        String formatted = TitleFormatter.formatWithVerifiedYear("Лабиринт Фавна (2024)", null);
        assertThat(formatted).isEqualTo("Лабиринт Фавна");
    }

    @Test
    void appendsYearWhenMissing() {
        String formatted = TitleFormatter.formatWithVerifiedYear("Хроники Нарнии: Лев, колдунья и платяной шкаф", 2005);
        assertThat(formatted).isEqualTo("Хроники Нарнии: Лев, колдунья и платяной шкаф (2005)");
    }
}
