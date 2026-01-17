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
        if (!tmdbClient.isEnabled()) {
            log.info("TMDB overview import disabled or API key missing; skipping plot backfill.");
            return;
        }
        log.info("Starting TMDB overview backfill...");
        long totalUpdated = 0;
        int batchSize = Math.max(1, properties.getBatchSize());
        long updateCap = Math.max(0, properties.getMaxUpdates());
        long cursor = 0L;
        while (true) {
            List<Movie> batch = movieRepository.findBatchMissingPlot(cursor, batchSize);
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
            if (updateCap > 0) {
                long remaining = updateCap - totalUpdated;
                if (remaining <= 0) {
                    break;
                }
                if (toUpdate.size() > remaining) {
                    toUpdate = new ArrayList<>(toUpdate.subList(0, (int) remaining));
                }
            }
            if (toUpdate.isEmpty()) {
                continue;
            }
            persistPlots(toUpdate);
            totalUpdated += toUpdate.size();
            log.info("Updated {} movie plots from TMDB (running total {}).", toUpdate.size(), totalUpdated);
            if (updateCap > 0 && totalUpdated >= updateCap) {
                log.info("Reached TMDB overview update cap of {}; stopping early.", updateCap);
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
