package com.gnemirko.movieRecsBot.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramMessageFormatterTest {

    @Test
    void sanitize_convertsMarkdownListToTelegramHtml() {
        String markdown = """
                Вот несколько фильмов, похожих на "Матрицу".

                1. **Начало (2010)** — Увлекательный триллер о мире сновидений, где границы реальности размыты, и каждый уровень сна полон опасностей.

                2. **Привидение (2015)** — Фильм о виртуальной реальности и ее влиянии на сознание, с захватывающим сюжетом и философскими вопросами.
                """;

        String html = TelegramMessageFormatter.sanitize(markdown);

        assertThat(html).doesNotContain("**");
        assertThat(html).contains("<b>Начало (2010)</b>");
        assertThat(html).contains("<b>Привидение (2015)</b>");
    }

    @Test
    void sanitizeAllowHtml_keepsAllowedTagsIntact() {
        String markdownLikeHtml = """
                Вот несколько интересных фэнтези фильмов, которые могут вам понравиться.

                1. <b>Властелин колец: Братство кольца</b> (2001) — Эпическая история.

                2. <b>Лабиринт Фавна</b> (2006) — Сказка.
                """;

        String html = TelegramMessageFormatter.sanitizeAllowBasicHtml(markdownLikeHtml);

        assertThat(html).contains("<b>Властелин колец: Братство кольца</b>");
        assertThat(html).contains("<b>Лабиринт Фавна</b>");
        assertThat(html).doesNotContain("&lt;b&gt;");
    }
}
