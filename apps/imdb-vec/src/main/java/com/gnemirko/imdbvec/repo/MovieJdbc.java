package com.gnemirko.imdbvec.repo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * JDBC layer for vector search + hybrid ranking.
 * Uses NamedParameterJdbcTemplate (no manual substitution).
 */
@Repository
public class MovieJdbc {

    private final JdbcTemplate jdbc;                 // for simple statements like SET
    private final NamedParameterJdbcTemplate np;     // for named-parameter SQL
    private final int efSearch;

    public record RecoRow(
            Long id,
            String tconst,
            String title,
            Short year,
            Double rating,
            Integer votes,
            double similarity
    ) {}

    public MovieJdbc(JdbcTemplate jdbc,
                     NamedParameterJdbcTemplate np,
                     @Value("${app.recommend.efSearch:200}") int efSearch) {
        this.jdbc = jdbc;
        this.np = np;
        this.efSearch = efSearch;
    }

    public int countCandidates(String[] includeGenres,
                               String[] excludeGenres,
                               Short fromYear,
                               Short toYear,
                               Integer runtimeMax,
                               Double minRating) {

        jdbc.execute("SET hnsw.ef_search = " + efSearch);

        String sql = """
            SELECT COUNT(*)
            FROM movie m
            WHERE (:fromYear IS NULL OR m.start_year >= :fromYear)
              AND (:toYear   IS NULL OR m.start_year <= :toYear)
              AND (:runtimeMax IS NULL OR m.runtime_minutes <= :runtimeMax)
              AND (:minRating IS NULL OR m.rating >= :minRating)
              AND (
                    COALESCE(:inc, ARRAY[]::text[]) = ARRAY[]::text[]
                    OR EXISTS (SELECT 1 FROM unnest(:inc) g WHERE g = ANY(m.genres))
                  )
              AND (
                    COALESCE(:exc, ARRAY[]::text[]) = ARRAY[]::text[]
                    OR NOT EXISTS (SELECT 1 FROM unnest(:exc) g WHERE g = ANY(m.genres))
                  )
            """;

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fromYear", fromYear)
                .addValue("toYear", toYear)
                .addValue("runtimeMax", runtimeMax)
                .addValue("minRating", minRating)
                .addValue("inc", includeGenres == null ? new String[]{} : includeGenres)
                .addValue("exc", excludeGenres == null ? new String[]{} : excludeGenres);

        return np.queryForObject(sql, p, Integer.class);
    }

    public List<RecoRow> topN(float[] queryVec,
                              String[] includeGenres,
                              String[] excludeGenres,
                              Short fromYear,
                              Short toYear,
                              Integer runtimeMax,
                              Double minRating,
                              int limit) {

        jdbc.execute("SET hnsw.ef_search = " + efSearch);

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < queryVec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(queryVec[i]);
        }
        sb.append(']');
        String vec = sb.toString();

        String sql = """
            WITH filtered AS (
              SELECT id, tconst, primary_title, start_year, rating, votes, embedding
              FROM movie m
              WHERE (:fromYear IS NULL OR m.start_year >= :fromYear)
                AND (:toYear   IS NULL OR m.start_year <= :toYear)
                AND (:runtimeMax IS NULL OR m.runtime_minutes <= :runtimeMax)
                AND (:minRating IS NULL OR m.rating >= :minRating)
                AND (
                      COALESCE(:inc, ARRAY[]::text[]) = ARRAY[]::text[]
                      OR EXISTS (SELECT 1 FROM unnest(:inc) g WHERE g = ANY(m.genres))
                    )
                AND (
                      COALESCE(:exc, ARRAY[]::text[]) = ARRAY[]::text[]
                      OR NOT EXISTS (SELECT 1 FROM unnest(:exc) g WHERE g = ANY(m.genres))
                    )
                AND m.embedding IS NOT NULL
            )
            SELECT id, tconst, primary_title, start_year, rating, votes,
                   (1 - (embedding <=> CAST(:vec AS vector))) AS sim,
                   (0.60*(1 - (embedding <=> CAST(:vec AS vector)))
                    + 0.30*LEAST(COALESCE(rating,0)/10.0,1.0)
                    + 0.10*LOG10(GREATEST(COALESCE(votes,0),1))) AS score
            FROM filtered
            ORDER BY score DESC
            LIMIT :limit
            """;

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fromYear", fromYear)
                .addValue("toYear", toYear)
                .addValue("runtimeMax", runtimeMax)
                .addValue("minRating", minRating)
                .addValue("inc", includeGenres == null ? new String[]{} : includeGenres)
                .addValue("exc", excludeGenres == null ? new String[]{} : excludeGenres)
                .addValue("vec", vec)
                .addValue("limit", limit);

        RowMapper<RecoRow> mapper = new RowMapper<>() {
            @Override public RecoRow mapRow(ResultSet rs, int rowNum) throws SQLException {
                return new RecoRow(
                        rs.getLong("id"),
                        rs.getString("tconst"),
                        rs.getString("primary_title"),
                        rs.getObject("start_year", Short.class),
                        rs.getObject("rating") == null ? null : rs.getDouble("rating"),
                        rs.getObject("votes") == null ? null : rs.getInt("votes"),
                        rs.getDouble("sim")
                );
            }
        };

        return np.query(sql, p, mapper);
    }
}