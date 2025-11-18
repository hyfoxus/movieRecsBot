package com.gnemirko.movieRecsBot.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationMessageClassifierTest {

    @Test
    void detectsNumberedBoldList() {
        String text = """
                Вот несколько интересных фэнтези фильмов, которые могут вам понравиться.

                1. <b>Властелин колец: Братство кольца</b> (2001) — Эпическая история.

                2. <b>Лабиринт Фавна</b> (2006) — Сказка.
                """;

        assertThat(RecommendationMessageClassifier.looksLikeRecommendation(text)).isTrue();
    }

    @Test
    void treatsQuestionLikeClarification() {
        String text = "Какой жанр ты любишь? Расскажи про любимые фильмы.";
        assertThat(RecommendationMessageClassifier.looksLikeRecommendation(text)).isFalse();
    }
}
