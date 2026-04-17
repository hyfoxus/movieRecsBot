package com.gnemirko.movieRecsBot.mcp;

import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.service.UserLanguage;
import com.gnemirko.movieRecsBot.service.recommendation.IntentType;
import com.gnemirko.movieRecsBot.service.recommendation.UserIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieContextServiceTest {

    @Mock
    private McpClient mcpClient;

    private MovieContextService service;

    @BeforeEach
    void setUp() {
        service = new MovieContextService(mcpClient);
    }

    @Test
    void buildContextBlockMergesProfileAndUserQuery() {
        UserProfile profile = new UserProfile();
        profile.getLikedGenres().add("Drama");
        profile.getBlocked().add("genre:horror");

        MovieContextItem item = new MovieContextItem(
                "tt123",
                "Evening Story",
                2021,
                8.1,
                15000,
                0.92,
                List.of("Drama"),
                List.of(new MovieContextItem.Person("nm1", "Actor One")),
                Map.of("plot", "A calm evening tale")
        );
        UserIntent intent = new UserIntent(
                List.of("Keanu Reeves"),
                List.of(),
                List.of(),
                List.of("moody"),
                null,
                "Фильм на вечер",
                "Moody drama with Keanu Reeves",
                IntentType.RECOMMENDATION,
                "",
                null,
                null
        );

        when(mcpClient.search(eq("Фильм на вечер | Vibe: moody | Prefers drama"), eq(List.of("Drama")), eq(List.of("horror")), eq(List.of("Keanu Reeves")), eq(null), eq(5)))
                .thenReturn(List.of(item));

        MovieContextService.ContextBlock block = service.buildContextBlock(
                "Фильм на вечер",
                "Prefers drama",
                profile,
                UserLanguage.fromIsoCode("ru"),
                intent,
                List.of("Keanu Reeves")
        );

        assertThat(block.block()).contains("CATALOG FACTS").contains("Evening Story");
        assertThat(block.items()).hasSize(1);
    }

    @Test
    void buildContextBlockReturnsEmptyWhenNoProfile() {
        MovieContextService.ContextBlock block = service.buildContextBlock(
                "request",
                "",
                null,
                UserLanguage.fromIsoCode("en"),
                UserIntent.empty(),
                List.of()
        );

        assertThat(block.block()).isEmpty();
        assertThat(block.items()).isEmpty();
    }

    @Test
    void buildContextBlockReturnsMovieForFiveActors() {
        UserProfile profile = new UserProfile();
        UserIntent intent = new UserIntent(
                List.of("Leonardo DiCaprio", "Brad Pitt", "Margot Robbie", "Al Pacino", "Damian Lewis"),
                List.of(),
                List.of(),
                List.of("retro"),
                null,
                "movie with those actors",
                "Retro movie starring DiCaprio ensemble",
                IntentType.RECOMMENDATION,
                "",
                null,
                2025
        );

        MovieContextItem item = new MovieContextItem(
                "tt7131622",
                "Once Upon a Time in... Hollywood",
                2019,
                7.6,
                800000,
                0.95,
                List.of("Comedy", "Drama"),
                List.of(new MovieContextItem.Person("nm0000138", "Leonardo DiCaprio")),
                Map.of()
        );

        List<String> actors = List.of("Leonardo DiCaprio", "Brad Pitt", "Margot Robbie", "Al Pacino", "Damian Lewis");
        when(mcpClient.search(
                eq("movie with those actors | Vibe: retro | Retro movie starring DiCaprio ensemble"),
                eq(List.of()),
                eq(List.of()),
                eq(actors),
                eq(2025),
                eq(5)
        )).thenReturn(List.of(item));

        MovieContextService.ContextBlock block = service.buildContextBlock(
                "movie with those actors",
                "Retro movie starring DiCaprio ensemble",
                profile,
                UserLanguage.fromIsoCode("en"),
                intent,
                actors
        );

        assertThat(block.items()).hasSize(1);
        assertThat(block.block()).contains("Once Upon a Time in... Hollywood");
    }

    @Test
    void buildContextBlockFallsBackToProfileWhenNoRecency() {
        UserProfile profile = new UserProfile();
        profile.getLikedGenres().add("Drama");
        UserIntent intent = new UserIntent(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                "",
                "",
                IntentType.RECOMMENDATION,
                "",
                null,
                null
        );
        service.buildContextBlock("anything", "", profile, UserLanguage.fromIsoCode("en"), intent, List.of());
        verify(mcpClient).search(anyString(), anyList(), anyList(), anyList(), eq(null), eq(5));
    }

    @Test
    void lookupByTitleUsesDeterministicLookup() {
        MovieContextItem match = new MovieContextItem(
                "tt9620292",
                "Let Him Go",
                2020,
                6.7,
                40000,
                0.99,
                List.of(),
                List.of(),
                Map.of()
        );
        when(mcpClient.lookupExact("Let Him Go", 2020)).thenReturn(Optional.of(match));

        Optional<MovieContextItem> result = service.lookupByTitle("Let Him Go", 2020);

        assertThat(result).contains(match);
        verify(mcpClient).lookupExact("Let Him Go", 2020);
        verifyNoMoreInteractions(mcpClient);
    }

    @Test
    void lookupByTitleFallsBackToSearchWhenExactMissing() {
        when(mcpClient.lookupExact("Let Him Go", 2020)).thenReturn(Optional.empty());
        MovieContextItem match = new MovieContextItem(
                "tt9620292",
                "Let Him Go",
                2020,
                6.7,
                40000,
                0.99,
                List.of(),
                List.of(),
                Map.of()
        );
        when(mcpClient.search(
                eq("Let Him Go 2020"),
                eq(List.of()),
                eq(List.of()),
                eq(List.of()),
                eq(2020),
                eq(5)
        )).thenReturn(List.of(match));

        Optional<MovieContextItem> result = service.lookupByTitle("Let Him Go", 2020);

        assertThat(result).contains(match);
    }

    @Test
    void buildContextBlockSanitizesActorFilters() {
        UserProfile profile = UserProfile.builder().telegramUserId(77L).build();
        MovieContextItem item = new MovieContextItem(
                "tt0000001",
                "Sample",
                2020,
                7.1,
                1200,
                0.87,
                List.of(),
                List.of(),
                Map.of()
        );
        when(mcpClient.search(anyString(), anyList(), anyList(), anyList(), any(), anyInt()))
                .thenReturn(List.of(item));

        service.buildContextBlock(
                "query",
                "",
                profile,
                UserLanguage.fromIsoCode("en"),
                UserIntent.empty(),
                List.of("  Actor One  ", "", "Actor One")
        );

        ArgumentCaptor<List<String>> filtersCaptor = ArgumentCaptor.forClass(List.class);
        verify(mcpClient).search(eq("query"), eq(List.of()), eq(List.of()), filtersCaptor.capture(), eq(null), eq(5));
        assertThat(filtersCaptor.getValue()).containsExactly("Actor One");
    }
}
