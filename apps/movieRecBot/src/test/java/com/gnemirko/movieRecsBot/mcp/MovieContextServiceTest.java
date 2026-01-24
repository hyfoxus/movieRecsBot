package com.gnemirko.movieRecsBot.mcp;

import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.service.UserLanguage;
import com.gnemirko.movieRecsBot.service.recommendation.UserIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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
                "Moody drama with Keanu Reeves"
        );

        when(mcpClient.search(eq("Фильм на вечер | Vibe: moody | Prefers drama"), eq(List.of("Drama")), eq(List.of("horror")), eq(List.of("Keanu Reeves")), eq(5)))
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
}
