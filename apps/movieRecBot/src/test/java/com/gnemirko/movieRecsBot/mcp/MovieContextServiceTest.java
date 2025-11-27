package com.gnemirko.movieRecsBot.mcp;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MovieContextServiceTest {

    @Test
    void formatCatalogEntry_includesCanonicalFields() {
        MovieContextItem item = new MovieContextItem(
                "tt0457430",
                "Лабиринт Фавна",
                2006,
                8.2,
                1800,
                0.97d,
                List.of("Fantasy", "Drama"),
                List.of(new MovieContextItem.Person("nm0000", "Иван Иванов")),
                Map.of("plot", "Темная сказка о мире Испании 1944 года.")
        );

        String entry = MovieContextService.formatCatalogEntry(1, item);

        assertThat(entry).contains("1) Title: Лабиринт Фавна");
        assertThat(entry).contains("Year: 2006");
        assertThat(entry).contains("Genres: Fantasy, Drama");
        assertThat(entry).contains("Plot: Темная сказка");
        assertThat(entry).contains("Similarity: 0.97");
        assertThat(entry).contains("IMDb ID: tt0457430");
        assertThat(entry).contains("Actors: Иван Иванов");
    }

    @Test
    void formatCatalogEntry_handlesMissingYear() {
        MovieContextItem item = new MovieContextItem(
                "tt9999999",
                "Сказка на ночь",
                null,
                null,
                null,
                0.50d,
                List.of(),
                List.of(),
                Map.of()
        );

        String entry = MovieContextService.formatCatalogEntry(2, item);

        assertThat(entry).contains("2) Title: Сказка на ночь");
        assertThat(entry).contains("Year: unknown");
    }
}
