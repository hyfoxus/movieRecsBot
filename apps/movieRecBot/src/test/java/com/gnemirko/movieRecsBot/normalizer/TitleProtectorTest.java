package com.gnemirko.movieRecsBot.normalizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TitleProtectorTest {

    @Test
    void protectsAndRestoresTitlesInsideBoldTags() {
        String text = """
                Здесь фильмы:
                1. <b>Ocean's Eleven</b> — Stylish heist.
                2. <b>Gravity</b> — Sci-fi thriller.
                """;

        TitleProtector protector = TitleProtector.protect(text);

        assertThat(protector.protectedText())
                .contains("__MOVIE_TITLE_0__")
                .contains("__MOVIE_TITLE_1__");

        String translated = protector.protectedText()
                .replace("__MOVIE_TITLE_0__", "__MOVIE_TITLE_0__ (перевод)")
                .replace("__MOVIE_TITLE_1__", "__MOVIE_TITLE_1__ (перевод)");

        String restored = protector.restore(translated);

        assertThat(restored)
                .contains("Ocean's Eleven")
                .contains("Gravity");
    }
}
