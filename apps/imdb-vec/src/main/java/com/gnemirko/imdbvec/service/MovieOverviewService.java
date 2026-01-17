package com.gnemirko.imdbvec.service;

import com.gnemirko.imdbvec.config.TmdbProperties;
import com.gnemirko.imdbvec.model.Movie;
import com.gnemirko.imdbvec.repo.MovieRepository;
import com.gnemirko.imdbvec.tmdb.TmdbClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class MovieOverviewService {

    private static final Logger log = LoggerFactory.getLogger(MovieOverviewService.class);

    private final MovieRepository movieRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TmdbClient tmdbClient;
    private final TmdbProperties properties;

    public MovieOverviewService(MovieRepository movieRepository,
                                JdbcTemplate jdbcTemplate,
                                TmdbClient tmdbClient,
                                TmdbProperties properties) {
        this.movieRepository = movieRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.tmdbClient = tmdbClient;
        this.properties = properties;
    }

    public void backfillOverviews() {
        backfillOverviews(null, null);
    }

    public void backfillOverviews(Long maxUpdatesOverride, Integer batchSizeOverride) {
        if (!tmdbClient.isEnabled()) {
            log.info("TMDB overview import disabled or API key missing; skipping plot backfill.");
            return;
        }
        long appliedMaxUpdates = maxUpdatesOverride != null && maxUpdatesOverride > 0 ? maxUpdatesOverride : Math.max(0, properties.getMaxUpdates());
        int appliedBatchSize = batchSizeOverride != null && batchSizeOverride > 0 ? batchSizeOverride : Math.max(1, properties.getBatchSize());
        log.info("Starting TMDB overview backfill... (batchSize={}, maxUpdates={})", appliedBatchSize, appliedMaxUpdates == 0 ? "unlimited" : appliedMaxUpdates);
        long totalUpdated = 0;
        long cursor = 0L;
        while (true) {
            List<Movie> batch = movieRepository.findBatchMissingPlot(cursor, appliedBatchSize);
            if (batch.isEmpty()) {
                break;
            }
            cursor = batch.get(batch.size() - 1).getId();
            List<Movie> toUpdate = new ArrayList<>();
            for (Movie movie : batch) {
                tmdbClient.fetchOverview(movie.getTconst())
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .ifPresent(overview -> {
                            movie.setPlot(overview);
                            movie.setEmbedding(null);
                            movie.setEmbeddingModel(null);
                            movie.setEmbeddingUpdatedAt(null);
                            toUpdate.add(movie);
                        });
            }
            if (toUpdate.isEmpty()) {
                continue;
            }
            List<Movie> cappedUpdates = toUpdate;
            List<Movie> cappedUpdates = toUpdate;
            if (appliedMaxUpdates > 0) {
                long remaining = appliedMaxUpdates - totalUpdated;
                if (remaining <= 0) {
                    break;
                }
                if (cappedUpdates.size() > remaining) {
                    cappedUpdates = new ArrayList<>(cappedUpdates.subList(0, (int) remaining));
                }
            }
            if (cappedUpdates.isEmpty()) {
                continue;
            }
            persistPlots(cappedUpdates);
            totalUpdated += cappedUpdates.size();
            log.info("Updated {} movie plots from TMDB (running total {}).", cappedUpdates.size(), totalUpdated);
            if (appliedMaxUpdates > 0 && totalUpdated >= appliedMaxUpdates) {
                log.info("Reached TMDB overview update cap of {}; stopping early.", appliedMaxUpdates);
                break;
            }
        }
        log.info("TMDB overview backfill finished. Movies updated: {}", totalUpdated);
    }

    @Transactional
    void persistPlots(List<Movie> movies) {
        jdbcTemplate.batchUpdate(
                "UPDATE movie SET plot = ?, embedding = NULL, embedding_model = NULL, embedding_updated_at = ? WHERE id = ?",
                movies,
                100,
                (ps, movie) -> {
                    ps.setString(1, movie.getPlot());
                    ps.setObject(2, null);
                    ps.setLong(3, movie.getId());
                }
        );
    }
}
