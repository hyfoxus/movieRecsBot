package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.mcp.MovieContextItem;
import com.gnemirko.movieRecsBot.mcp.MovieContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieInfoServiceTest {

    @Mock
    private MovieContextService movieContextService;

    private MovieInfoService service;

    @BeforeEach
    void setUp() {
        service = new MovieInfoService(movieContextService);
    }

    @Test
    void describeMovieFormatsResult() {
        MovieContextItem item = new MovieContextItem(
                "tt0113277",
                "Heat",
                1995,
                8.3,
                650000,
                0.99,
                List.of("Crime", "Drama"),
                List.of(new MovieContextItem.Person("nm0000199", "Robert De Niro")),
                Map.of("plot", "Thief vs cop in LA.", "runtimeMinutes", 170)
        );
        when(movieContextService.lookupByTitle("Heat", 1995)).thenReturn(Optional.of(item));

        String reply = service.describeMovie("Heat", 1995);

        assertThat(reply)
                .contains("<b>Heat (1995)</b>")
                .contains("⭐ 8.3")
                .contains("Жанры: Crime, Drama")
                .contains("В ролях: Robert De Niro")
                .contains("IMDb ID: tt0113277");
        verify(movieContextService).lookupByTitle("Heat", 1995);
    }

    @Test
    void describeMovieHandlesMisses() {
        when(movieContextService.lookupByTitle("Unknown", null)).thenReturn(Optional.empty());

        String reply = service.describeMovie("Unknown", null);

        assertThat(reply).contains("Unknown");
    }

    @Test
    void describeMovieRequiresTitle() {
        String reply = service.describeMovie("   ", null);
        assertThat(reply).contains("назови");
    }

    @Test
    void describeMovieFallsBackWhenYearMismatch() {
        MovieContextItem item2019 = new MovieContextItem(
                "tt6412452",
                "The Highwaymen",
                2019,
                6.8,
                160000,
                0.95,
                List.of("Crime", "Drama"),
                List.of(),
                Map.of()
        );
        when(movieContextService.lookupByTitle("The Highwaymen", 2018))
                .thenReturn(Optional.of(item2019));

        String reply = service.describeMovie("The Highwaymen", 2018);

        assertThat(reply).contains("The Highwaymen");
        verify(movieContextService).lookupByTitle("The Highwaymen", 2018);
    }
}
