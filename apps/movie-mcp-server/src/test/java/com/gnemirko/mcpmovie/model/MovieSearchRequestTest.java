package com.gnemirko.mcpmovie.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MovieSearchRequestTest {

    @Test
    void sanitizesListsAndQuery() {
        MovieSearchRequest request = new MovieSearchRequest(
                "  comedy ",
                5,
                null,
                null,
                null,
                null,
                List.of(" Action ", "  "),
                List.of("Drama", null),
                List.of("Tom Hanks", "  Meryl streep ")
        );

        assertThat(request.query()).isEqualTo("comedy");
        assertThat(request.includeGenres()).containsExactly("Action");
        assertThat(request.excludeGenres()).containsExactly("Drama");
        assertThat(request.actors()).containsExactly("tom hanks", "meryl streep");
    }

    @Test
    void defaultsToEmptyLists() {
        MovieSearchRequest request = new MovieSearchRequest(
                "thriller",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(request.includeGenres()).isEmpty();
        assertThat(request.excludeGenres()).isEmpty();
        assertThat(request.actors()).isEmpty();
    }
}
