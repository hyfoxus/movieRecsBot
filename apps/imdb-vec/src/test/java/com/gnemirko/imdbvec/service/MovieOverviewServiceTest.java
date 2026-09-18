package com.gnemirko.imdbvec.service;

import com.gnemirko.imdbvec.config.TmdbProperties;
import com.gnemirko.imdbvec.model.Movie;
import com.gnemirko.imdbvec.repo.MovieRepository;
import com.gnemirko.imdbvec.tmdb.TmdbClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieOverviewServiceTest {

    @Mock
    private MovieRepository movieRepository;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private TmdbClient tmdbClient;

    private TmdbProperties properties;
    private MovieOverviewService service;

    @BeforeEach
    void setUp() {
        properties = new TmdbProperties();
        properties.setBatchSize(50);
        properties.setMaxUpdates(0);
        service = new MovieOverviewService(movieRepository, jdbcTemplate, tmdbClient, properties);
    }

    private Movie movie(long id, String tconst) {
        Movie movie = new Movie();
        movie.setId(id);
        movie.setTconst(tconst);
        return movie;
    }

    @Test
    void backfillOverviewsSkipsEntirelyWhenTmdbClientDisabled() {
        when(tmdbClient.isEnabled()).thenReturn(false);

        service.backfillOverviews();

        verifyNoInteractions(movieRepository);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void backfillOverviewsUpdatesOnlyMoviesWithNonBlankOverview() {
        when(tmdbClient.isEnabled()).thenReturn(true);
        Movie hasOverview = movie(1, "tt0000001");
        Movie noOverview = movie(2, "tt0000002");
        Movie blankOverview = movie(3, "tt0000003");
        when(movieRepository.findBatchMissingPlot(anyLong(), anyInt()))
                .thenReturn(List.of(hasOverview, noOverview, blankOverview))
                .thenReturn(List.of());
        when(tmdbClient.fetchOverview("tt0000001")).thenReturn(Optional.of("A real plot."));
        when(tmdbClient.fetchOverview("tt0000002")).thenReturn(Optional.empty());
        when(tmdbClient.fetchOverview("tt0000003")).thenReturn(Optional.of("   "));

        service.backfillOverviews();

        ArgumentCaptor<List<Movie>> captor = ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate).batchUpdate(anyString(), captor.capture(), eq(100), any());
        List<Movie> updated = captor.getValue();
        assertThat(updated).containsExactly(hasOverview);
        assertThat(hasOverview.getPlot()).isEqualTo("A real plot.");
        assertThat(hasOverview.getEmbedding()).isNull();
        assertThat(hasOverview.getEmbeddingModel()).isNull();
    }

    @Test
    void backfillOverviewsDoesNothingWhenNoBatchesReturned() {
        when(tmdbClient.isEnabled()).thenReturn(true);
        when(movieRepository.findBatchMissingPlot(anyLong(), anyInt())).thenReturn(List.of());

        service.backfillOverviews();

        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(List.class), anyInt(), any());
    }

    @Test
    void backfillOverviewsRespectsMaxUpdatesCapAcrossBatches() {
        when(tmdbClient.isEnabled()).thenReturn(true);
        properties.setMaxUpdates(3);

        Movie m1 = movie(1, "tt1");
        Movie m2 = movie(2, "tt2");
        Movie m3 = movie(3, "tt3");
        Movie m4 = movie(4, "tt4");

        when(movieRepository.findBatchMissingPlot(anyLong(), anyInt()))
                .thenReturn(List.of(m1, m2))
                .thenReturn(List.of(m3, m4))
                .thenReturn(List.of());
        when(tmdbClient.fetchOverview(anyString())).thenReturn(Optional.of("plot"));

        service.backfillOverviews();

        ArgumentCaptor<List<Movie>> captor = ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate, times(2)).batchUpdate(anyString(), captor.capture(), eq(100), any());
        int totalUpdated = captor.getAllValues().stream().mapToInt(List::size).sum();
        assertThat(totalUpdated).isEqualTo(3);
        // repository should not be asked for a third batch once the cap is reached
        verify(movieRepository, times(2)).findBatchMissingPlot(anyLong(), anyInt());
    }

    @Test
    void backfillOverviewsWithExplicitOverridesIgnoresConfiguredDefaults() {
        when(tmdbClient.isEnabled()).thenReturn(true);
        Movie m1 = movie(1, "tt1");
        when(movieRepository.findBatchMissingPlot(anyLong(), anyInt()))
                .thenReturn(List.of(m1))
                .thenReturn(List.of());
        when(tmdbClient.fetchOverview("tt1")).thenReturn(Optional.of("plot"));

        service.backfillOverviews(10L, 25);

        verify(movieRepository, times(2)).findBatchMissingPlot(anyLong(), eq(25));
    }
}
