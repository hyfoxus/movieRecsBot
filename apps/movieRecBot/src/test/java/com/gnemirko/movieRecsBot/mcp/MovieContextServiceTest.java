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

        when(mcpClient.search(any(McpSearchRequest.class))).thenReturn(List.of(item));

        MovieContextService.ContextBlock block = service.buildContextBlock(
                "Фильм на вечер",
                "Prefers drama",
                profile,
                UserLanguage.fromIsoCode("ru"),
                intent,
                List.of("Keanu Reeves")
        );

        ArgumentCaptor<McpSearchRequest> searchCaptor = ArgumentCaptor.forClass(McpSearchRequest.class);
        verify(mcpClient).search(searchCaptor.capture());
        McpSearchRequest request = searchCaptor.getValue();
        assertThat(request.query()).isEqualTo("Фильм на вечер | Vibe: moody | Prefers drama");
        assertThat(request.includeGenres()).containsExactly("Drama");
        assertThat(request.excludeGenres()).containsExactly("horror");
        assertThat(request.actors()).containsExactly("Keanu Reeves");
        assertThat(request.limit()).isEqualTo(5);
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
        when(mcpClient.search(any(McpSearchRequest.class))).thenReturn(List.of(item));

        MovieContextService.ContextBlock block = service.buildContextBlock(
                "movie with those actors",
                "Retro movie starring DiCaprio ensemble",
                profile,
                UserLanguage.fromIsoCode("en"),
                intent,
                actors
        );

        ArgumentCaptor<McpSearchRequest> captor = ArgumentCaptor.forClass(McpSearchRequest.class);
        verify(mcpClient).search(captor.capture());
        McpSearchRequest request = captor.getValue();
        assertThat(request.query()).isEqualTo("movie with those actors | Vibe: retro | Retro movie starring DiCaprio ensemble");
        assertThat(request.actors()).containsExactlyElementsOf(actors);
        assertThat(request.fromYear()).isEqualTo(2025);
        assertThat(request.limit()).isEqualTo(5);
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
        when(mcpClient.search(any(McpSearchRequest.class))).thenReturn(List.of());

        service.buildContextBlock("anything", "", profile, UserLanguage.fromIsoCode("en"), intent, List.of());

        ArgumentCaptor<McpSearchRequest> captor = ArgumentCaptor.forClass(McpSearchRequest.class);
        verify(mcpClient).search(captor.capture());
        assertThat(captor.getValue().includeGenres()).containsExactly("Drama");
        assertThat(captor.getValue().excludeGenres()).isEmpty();
        assertThat(captor.getValue().fromYear()).isNull();
        assertThat(captor.getValue().limit()).isEqualTo(5);
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
        when(mcpClient.lookupExact(any(McpLookupRequest.class))).thenReturn(Optional.of(match));

        Optional<MovieContextItem> result = service.lookupByTitle("Let Him Go", 2020);

        assertThat(result).contains(match);
        ArgumentCaptor<McpLookupRequest> lookupCaptor = ArgumentCaptor.forClass(McpLookupRequest.class);
        verify(mcpClient).lookupExact(lookupCaptor.capture());
        McpLookupRequest lookupRequest = lookupCaptor.getValue();
        assertThat(lookupRequest.title()).isEqualTo("Let Him Go");
        assertThat(lookupRequest.year()).isEqualTo(2020);
        verifyNoMoreInteractions(mcpClient);
    }

    @Test
    void lookupByTitleFallsBackToSearchWhenExactMissing() {
        when(mcpClient.lookupExact(any(McpLookupRequest.class))).thenReturn(Optional.empty());
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
        when(mcpClient.search(any(McpSearchRequest.class))).thenReturn(List.of(match));

        Optional<MovieContextItem> result = service.lookupByTitle("Let Him Go", 2020);

        assertThat(result).contains(match);
        ArgumentCaptor<McpSearchRequest> searchCaptor = ArgumentCaptor.forClass(McpSearchRequest.class);
        verify(mcpClient).search(searchCaptor.capture());
        assertThat(searchCaptor.getValue().query()).isEqualTo("Let Him Go 2020");
        assertThat(searchCaptor.getValue().fromYear()).isEqualTo(2020);
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
        when(mcpClient.search(any(McpSearchRequest.class))).thenReturn(List.of(item));

        service.buildContextBlock(
                "query",
                "",
                profile,
                UserLanguage.fromIsoCode("en"),
                UserIntent.empty(),
                List.of("  Actor One  ", "", "Actor One")
        );

        ArgumentCaptor<McpSearchRequest> captor = ArgumentCaptor.forClass(McpSearchRequest.class);
        verify(mcpClient).search(captor.capture());
        assertThat(captor.getValue().query()).isEqualTo("query");
        assertThat(captor.getValue().actors()).containsExactly("Actor One");
        assertThat(captor.getValue().includeGenres()).isEmpty();
        assertThat(captor.getValue().excludeGenres()).isEmpty();
    }
}
