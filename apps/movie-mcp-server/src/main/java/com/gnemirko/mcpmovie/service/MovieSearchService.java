package com.gnemirko.mcpmovie.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.mcpmovie.config.MovieMcpProperties;
import com.gnemirko.mcpmovie.model.MovieActor;
import com.gnemirko.mcpmovie.model.MovieContext;
import com.gnemirko.mcpmovie.model.MovieSearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class MovieSearchService {

    private static final Logger log = LoggerFactory.getLogger(MovieSearchService.class);

    private static final String SEARCH_SQL = """
            WITH filtered AS (
                SELECT id, tconst, primary_title, start_year, rating, votes,
                       genres, plot, title_type, runtime_minutes, is_adult, embedding
                FROM movie m
                WHERE %s
            )
            SELECT filtered.tconst,
                   filtered.primary_title,
                   filtered.start_year,
                   filtered.rating,
                   filtered.votes,
                   filtered.genres,
                   filtered.plot,
                   filtered.title_type,
                   filtered.runtime_minutes,
                   filtered.is_adult,
                   (1 - (filtered.embedding <=> CAST(:vec AS vector))) AS similarity,
                   actors.actor_list
            FROM filtered
            LEFT JOIN LATERAL (
                SELECT json_agg(obj) AS actor_list
                FROM (
                    SELECT json_build_object('id', p.nconst, 'name', p.primary_name) AS obj
                    FROM movie_principal mp
                    JOIN person p ON p.id = mp.person_id
                    WHERE mp.movie_id = filtered.id
                      AND mp.category IN ('actor','actress')
                    ORDER BY mp.ordering NULLS LAST, p.primary_name
                    LIMIT 5
                ) actor_rows
            ) actors ON TRUE
            ORDER BY similarity DESC
            LIMIT :limit
            """;

    private static final String RESOURCE_SQL = """
            SELECT m.tconst, m.primary_title, m.start_year, m.rating, m.votes, m.genres,
                   m.plot, m.title_type, m.runtime_minutes, m.is_adult,
                   actors.actor_list
            FROM movie m
            LEFT JOIN LATERAL (
                SELECT json_agg(obj) AS actor_list
                FROM (
                    SELECT json_build_object('id', p.nconst, 'name', p.primary_name) AS obj
                    FROM movie_principal mp
                    JOIN person p ON p.id = mp.person_id
                    WHERE mp.movie_id = m.id
                      AND mp.category IN ('actor','actress')
                    ORDER BY mp.ordering NULLS LAST, p.primary_name
                    LIMIT 5
                ) actor_rows
            ) actors ON TRUE
            WHERE m.tconst = :tconst
            """;

    private static final TypeReference<List<Map<String, Object>>> ACTOR_LIST_TYPE =
            new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;
    private final MovieMcpProperties properties;

    public MovieSearchService(NamedParameterJdbcTemplate jdbcTemplate,
                              EmbeddingModel embeddingModel,
                              ObjectMapper objectMapper,
                              MovieMcpProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<MovieContext> search(MovieSearchRequest request) {
        int max = Math.max(1, properties.maxResults());
        int requested = request.limit() == null ? max : Math.max(1, request.limit());
        int limit = Math.min(requested, max);

        long start = System.nanoTime();
        float[] embedding = embeddingModel.embed(request.query());
        Duration embedDuration = Duration.ofNanos(System.nanoTime() - start);
        log.debug("Computed embedding in {} ms", embedDuration.toMillis());

        String vectorLiteral = toVectorLiteral(embedding);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("vec", vectorLiteral)
                .addValue("limit", limit);

        List<String> where = new ArrayList<>();
        where.add("m.embedding IS NOT NULL");
        where.add("LOWER(m.title_type) IN ('movie','tvmovie')");

        if (request.fromYear() != null) {
            where.add("m.start_year >= :fromYear");
            params.addValue("fromYear", request.fromYear());
        }
        if (request.toYear() != null) {
            where.add("m.start_year <= :toYear");
            params.addValue("toYear", request.toYear());
        }
        if (request.runtimeMinutes() != null) {
            where.add("m.runtime_minutes <= :runtimeMax");
            params.addValue("runtimeMax", request.runtimeMinutes());
        }
        if (request.minRating() != null) {
            where.add("m.rating >= :minRating");
            params.addValue("minRating", request.minRating());
        }

        if (!request.includeGenres().isEmpty()) {
            where.add("""
                    EXISTS (
                       SELECT 1 FROM unnest(string_to_array(:incGenres, ',')) g
                       WHERE g = ANY(m.genres)
                    )
                    """);
            params.addValue("incGenres", String.join(",", request.includeGenres()));
        }
        if (!request.excludeGenres().isEmpty()) {
            where.add("""
                    NOT EXISTS (
                       SELECT 1 FROM unnest(string_to_array(:excGenres, ',')) g
                       WHERE g = ANY(m.genres)
                    )
                    """);
            params.addValue("excGenres", String.join(",", request.excludeGenres()));
        }

        List<String> actorPatterns = buildActorPatterns(request.actors());
        for (int i = 0; i < actorPatterns.size(); i++) {
            where.add("""
                    EXISTS (
                      SELECT 1
                      FROM movie_principal mp
                      JOIN person p ON p.id = mp.person_id
                      WHERE mp.movie_id = m.id
                        AND mp.category IN ('actor','actress')
                        AND regexp_replace(LOWER(p.primary_name), '[^a-z0-9]', '', 'g')
                            LIKE :actorPattern%s
                    )
                    """.formatted(i));
            params.addValue("actorPattern" + i, actorPatterns.get(i));
        }

        String whereSql = String.join(" AND ", where);
        String sql = String.format(Locale.ROOT, SEARCH_SQL, whereSql);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapMovie(rs, false));
    }

    @Transactional(readOnly = true)
    public Optional<MovieContext> fetchByTconst(String tconst) {
        MapSqlParameterSource params = new MapSqlParameterSource("tconst", tconst);
        List<MovieContext> matches = jdbcTemplate.query(RESOURCE_SQL, params, (rs, rowNum) -> mapMovie(rs, true));
        return matches.stream().findFirst();
    }

    private MovieContext mapMovie(ResultSet rs, boolean forceSimilarityOne) throws SQLException {
        String tconst = rs.getString("tconst");
        String title = rs.getString("primary_title");
        Integer year = (Integer) rs.getObject("start_year");
        Double rating = (Double) rs.getObject("rating");
        Integer votes = (Integer) rs.getObject("votes");
        Double similarity = null;
        try {
            similarity = (Double) rs.getObject("similarity");
        } catch (SQLException ignored) {
            // column is missing for fetchByTconst queries
        }
        Array genresArray = rs.getArray("genres");
        List<String> genres = genresArray == null ? List.of() : readGenres(genresArray);
        String plot = rs.getString("plot");
        String titleType = rs.getString("title_type");
        Integer runtime = (Integer) rs.getObject("runtime_minutes");
        Boolean isAdult = (Boolean) rs.getObject("is_adult");
        List<MovieActor> actors = readActors(rs.getString("actor_list"));

        Map<String, Object> metadata = new LinkedHashMap<>();
        if (plot != null) {
            metadata.put("plot", plot);
        }
        if (titleType != null) {
            metadata.put("titleType", titleType);
        }
        if (runtime != null) {
            metadata.put("runtimeMinutes", runtime);
        }
        if (isAdult != null) {
            metadata.put("isAdult", isAdult);
        }

        double safeSimilarity = forceSimilarityOne ? 1.0d : (similarity == null ? 0d : similarity);
        return new MovieContext(
                tconst,
                title,
                year,
                rating,
                votes,
                safeSimilarity,
                genres,
                actors,
                Map.copyOf(metadata)
        );
    }

    private static List<String> readGenres(Array array) throws SQLException {
        Object raw = array.getArray();
        if (!(raw instanceof Object[] values)) {
            return List.of();
        }
        List<String> genres = new ArrayList<>(values.length);
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = value.toString().trim();
            if (!text.isEmpty()) {
                genres.add(text);
            }
        }
        return List.copyOf(genres);
    }

    private List<MovieActor> readActors(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(json, ACTOR_LIST_TYPE);
            return rows.stream()
                    .filter(Objects::nonNull)
                    .map(row -> new MovieActor(
                            Objects.toString(row.get("id"), ""),
                            Objects.toString(row.get("name"), "")
                    ))
                    .filter(actor -> !actor.id().isBlank() && !actor.name().isBlank())
                    .toList();
        } catch (Exception ex) {
            throw new SQLException("Failed to deserialize actor_list JSON", ex);
        }
    }

    private static String toVectorLiteral(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("Embedding vector is empty");
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.US, "%.8f", embedding[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    private List<String> buildActorPatterns(List<String> actors) {
        if (actors == null || actors.isEmpty()) {
            return List.of();
        }
        return actors.stream()
                .map(this::sanitizeActorToken)
                .filter(s -> !s.isEmpty())
                .map(token -> "%" + token + "%")
                .toList();
    }

    private String sanitizeActorToken(String raw) {
        if (raw == null) {
            return "";
        }
        String ascii = raw.toLowerCase(Locale.ROOT);
        return ascii.replaceAll("[^a-z0-9]", "");
    }
}
