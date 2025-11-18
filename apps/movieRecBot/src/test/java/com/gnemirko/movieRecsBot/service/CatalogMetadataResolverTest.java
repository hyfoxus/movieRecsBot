package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.mcp.MovieContextItem;
import com.gnemirko.movieRecsBot.mcp.MovieContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogMetadataResolverTest {

    private StubMovieContextService movieContextService;

    private CatalogMetadataResolver resolver;

    @BeforeEach
    void setUp() {
        movieContextService = new StubMovieContextService();
        resolver = new CatalogMetadataResolver(movieContextService);
    }

    @Test
    void overridesYearFromContextItems() {
        var movie = new RecommendationService.Movie();
        movie.title = "Inception";
        movie.year = 2025;
        MovieContextItem contextItem = new MovieContextItem(
                "tt1375666",
                "Inception",
                2010,
                8.8,
                2000,
                0.99d,
                List.of("Sci-Fi"),
                List.of(),
                Map.of()
        );

        resolver.reconcileYears(List.of(movie), List.of(contextItem));

        assertThat(movie.year).isEqualTo(2010);
        assertThat(movieContextService.recordedQueries()).isEmpty();
    }

    @Test
    void fallsBackToLookupWhenContextMissing() {
        var movie = new RecommendationService.Movie();
        movie.title = "Interstellar";
        movie.year = null;

        MovieContextItem lookedUp = new MovieContextItem(
                "tt0816692",
                "Interstellar",
                2014,
                8.6,
                1500,
                0.98d,
                List.of("Adventure", "Drama"),
                List.of(),
                Map.of()
        );
        movieContextService.stubLookup("Interstellar", Optional.of(lookedUp));

        resolver.reconcileYears(List.of(movie), List.of());

        assertThat(movie.year).isEqualTo(2014);
        assertThat(movieContextService.recordedQueries()).containsExactly("Interstellar");
    }

    @Test
    void resolvesYearForLocalizedTitle() {
        var movie = new RecommendationService.Movie();
        movie.title = "Властелин колец: Братство кольца";
        movie.year = 2024;

        MovieContextItem russianTitle = new MovieContextItem(
                "tt0120737",
                "The Lord of the Rings: The Fellowship of the Ring",
                2001,
                8.9,
                3000,
                0.97d,
                List.of("Fantasy"),
                List.of(),
                Map.of()
        );
        movieContextService.stubLookup("Властелин колец: Братство кольца", Optional.of(russianTitle));

        resolver.reconcileYears(List.of(movie), List.of());

        assertThat(movie.year).isEqualTo(2001);
        assertThat(movieContextService.recordedQueries()).containsExactly("Властелин колец: Братство кольца");
    }

    @Test
    void stripsFakeYearBeforeLookup() {
        var movie = new RecommendationService.Movie();
        movie.title = "Лабиринт Фавна (2024)";
        movie.year = 2024;

        MovieContextItem canonical = new MovieContextItem(
                "tt0457430",
                "Pan's Labyrinth",
                2006,
                8.2,
                1800,
                0.96d,
                List.of("Fantasy"),
                List.of(),
                Map.of()
        );
        movieContextService.stubLookup("Лабиринт Фавна", Optional.of(canonical));

        resolver.reconcileYears(List.of(movie), List.of());

        assertThat(movie.year).isEqualTo(2006);
        assertThat(movieContextService.recordedQueries()).containsExactly("Лабиринт Фавна");
    }

    @Test
    void removesYearWhenMetadataUnavailable() {
        var movie = new RecommendationService.Movie();
        movie.title = "Zodiac";
        movie.year = 2023;

        movieContextService.stubLookup("Zodiac", Optional.empty());

        resolver.reconcileYears(List.of(movie), List.of());

        assertThat(movie.year).isNull();
        assertThat(movieContextService.recordedQueries()).containsExactly("Zodiac");
    }

    private static final class StubMovieContextService extends MovieContextService {
        private final Map<String, Optional<MovieContextItem>> lookups = new java.util.HashMap<>();
        private final java.util.List<String> queries = new java.util.ArrayList<>();

        StubMovieContextService() {
            super(null);
        }

        void stubLookup(String title, Optional<MovieContextItem> result) {
            lookups.put(title, result);
        }

        java.util.List<String> recordedQueries() {
            return queries;
        }

        @Override
        public Optional<MovieContextItem> lookupByTitle(String title) {
            queries.add(title);
            return lookups.getOrDefault(title, Optional.empty());
        }
    }
}
