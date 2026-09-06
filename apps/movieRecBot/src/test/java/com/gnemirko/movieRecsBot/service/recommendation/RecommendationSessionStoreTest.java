package com.gnemirko.movieRecsBot.service.recommendation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationSessionStoreTest {

    private final RecommendationSessionStore store = new RecommendationSessionStore();

    private RecommendationMovie movie(String title, Integer year) {
        RecommendationMovie m = new RecommendationMovie();
        m.setTitle(title);
        m.setYear(year);
        m.setGenres(Set.of("Drama"));
        return m;
    }

    @Test
    void recordsLastBatchAndExposesExcludedKeys() {
        long chatId = 1L;
        store.record(chatId, "cozy movie", List.of(movie("Heat", 1995), movie("Inception", 2010)));

        assertThat(store.get(chatId)).isPresent();
        assertThat(store.get(chatId).get().lastMovies()).hasSize(2);
        assertThat(store.excludedTitleKeys(chatId)).containsExactlyInAnyOrder("heat|1995", "inception|2010");
    }

    @Test
    void excludedKeysAccumulateAcrossCalls() {
        long chatId = 2L;
        store.record(chatId, "first", List.of(movie("Heat", 1995)));
        store.record(chatId, "second", List.of(movie("Inception", 2010)));

        assertThat(store.excludedTitleKeys(chatId)).containsExactlyInAnyOrder("heat|1995", "inception|2010");
        assertThat(store.get(chatId).get().lastPromptText()).isEqualTo("second");
        assertThat(store.get(chatId).get().lastMovies()).extracting(RecommendationMovie::getTitle).containsExactly("Inception");
    }

    @Test
    void unknownChatHasNoSession() {
        assertThat(store.get(999L)).isEmpty();
        assertThat(store.excludedTitleKeys(999L)).isEmpty();
    }

    @Test
    void clearRemovesSession() {
        long chatId = 3L;
        store.record(chatId, "prompt", List.of(movie("Heat", 1995)));
        store.clear(chatId);

        assertThat(store.get(chatId)).isEmpty();
    }
}
