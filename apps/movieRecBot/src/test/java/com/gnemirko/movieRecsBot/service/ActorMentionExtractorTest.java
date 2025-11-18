package com.gnemirko.movieRecsBot.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ActorMentionExtractorTest {

    @Test
    void extractsEnglishFullName() {
        Set<String> names = ActorMentionExtractor.extract("Show me movies with Keanu Reeves please.");
        assertThat(names).containsExactly("Keanu Reeves");
    }

    @Test
    void extractsCyrillicFullName() {
        Set<String> names = ActorMentionExtractor.extract("Хочу фильм с Киану Ривз или Томом Хэнксом.");
        assertThat(names).contains("Киану Ривз")
                .contains("Томом Хэнксом");
    }

    @Test
    void ignoresSingleWords() {
        Set<String> names = ActorMentionExtractor.extract("фильм Киану");
        assertThat(names).isEmpty();
    }
}
