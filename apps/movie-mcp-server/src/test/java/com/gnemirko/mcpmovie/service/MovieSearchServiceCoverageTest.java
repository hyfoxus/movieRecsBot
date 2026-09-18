package com.gnemirko.mcpmovie.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.mcpmovie.config.MovieMcpProperties;
import com.gnemirko.mcpmovie.model.MovieContext;
import com.gnemirko.mcpmovie.model.MovieSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers MovieSearchService branches not exercised by MovieSearchServiceTest: row mapping
 * (mapMovie via the captured RowMapper), fetchByTconst/lookupByTitle, filter/actor param
 * building, and cache eviction beyond capacity.
 */
@ExtendWith(MockitoExtension.class)
class MovieSearchServiceCoverageTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Mock
    private EmbeddingModel embeddingModel;

    private MovieSearchService service;

    @BeforeEach
    void setUp() {
        MovieMcpProperties properties = new MovieMcpProperties("name", "1.0.0", "desc", 15, 300);
        service = new MovieSearchService(jdbcTemplate, embeddingModel, new ObjectMapper(), properties);
    }

    private ResultSet mockRow(String tconst, String title, Integer year, Double rating, Integer votes,
                              Double similarity, String[] genres, String plot, String titleType,
                              Integer runtime, Boolean isAdult, String actorListJson) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("tconst")).thenReturn(tconst);
        when(rs.getString("primary_title")).thenReturn(title);
        when(rs.getObject("start_year")).thenReturn(year);
        when(rs.getObject("rating")).thenReturn(rating);
        when(rs.getObject("votes")).thenReturn(votes);
        if (similarity == null) {
            when(rs.getObject("similarity")).thenThrow(new SQLException("no similarity column"));
        } else {
            when(rs.getObject("similarity")).thenReturn(similarity);
        }
        if (genres == null) {
            when(rs.getArray("genres")).thenReturn(null);
        } else {
            Array array = mock(Array.class);
            when(array.getArray()).thenReturn((Object[]) genres);
            when(rs.getArray("genres")).thenReturn(array);
        }
        when(rs.getString("plot")).thenReturn(plot);
        when(rs.getString("title_type")).thenReturn(titleType);
        when(rs.getObject("runtime_minutes")).thenReturn(runtime);
        when(rs.getObject("is_adult")).thenReturn(isAdult);
        when(rs.getString("actor_list")).thenReturn(actorListJson);
        return rs;
    }

    @SuppressWarnings("unchecked")
    private RowMapper<MovieContext> captureSearchRowMapper(MovieSearchRequest request) {
        ArgumentCaptor<RowMapper<MovieContext>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.5f});
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), captor.capture()))
                .thenReturn(List.of());
        service.search(request);
        return captor.getValue();
    }

    @Test
    void searchRowMapperMapsFullRowIncludingActorsGenresAndMetadata() throws SQLException {
        MovieSearchRequest request = new MovieSearchRequest("noir", 5, null, null, null, null,
                List.of(), List.of(), List.of());
        RowMapper<MovieContext> mapper = captureSearchRowMapper(request);

        ResultSet rs = mockRow("tt1", "Heat", 1995, 8.3, 500000, 0.87,
                new String[]{"Crime", "Drama"}, "A cop chases a thief.", "movie", 170, false,
                "[{\"id\":\"nm1\",\"name\":\"Al Pacino\"},{\"id\":\"nm2\",\"name\":\"Robert De Niro\"}]");

        MovieContext movie = mapper.mapRow(rs, 1);

        assertThat(movie.tconst()).isEqualTo("tt1");
        assertThat(movie.title()).isEqualTo("Heat");
        assertThat(movie.year()).isEqualTo(1995);
        assertThat(movie.rating()).isEqualTo(8.3);
        assertThat(movie.votes()).isEqualTo(500000);
        assertThat(movie.similarity()).isEqualTo(0.87);
        assertThat(movie.genres()).containsExactly("Crime", "Drama");
        assertThat(movie.actors()).extracting("name").containsExactly("Al Pacino", "Robert De Niro");
        assertThat(movie.metadata())
                .containsEntry("plot", "A cop chases a thief.")
                .containsEntry("titleType", "movie")
                .containsEntry("runtimeMinutes", 170)
                .containsEntry("isAdult", false);
    }

    @Test
    void searchRowMapperHandlesNullGenresActorsAndOptionalMetadata() throws SQLException {
        MovieSearchRequest request = new MovieSearchRequest("obscure film", null, null, null, null, null,
                List.of(), List.of(), List.of());
        RowMapper<MovieContext> mapper = captureSearchRowMapper(request);

        ResultSet rs = mockRow("tt2", "Obscure Film", null, null, null, null,
                null, null, null, null, null, null);

        MovieContext movie = mapper.mapRow(rs, 1);

        assertThat(movie.genres()).isEmpty();
        assertThat(movie.actors()).isEmpty();
        assertThat(movie.metadata()).isEmpty();
        // similarity column missing (throws SQLException) and forceSimilarityOne=false -> defaults to 0
        assertThat(movie.similarity()).isEqualTo(0d);
    }

    @Test
    void searchAppliesYearRuntimeRatingGenreAndActorFilters() {
        MovieSearchRequest request = new MovieSearchRequest(
                "gritty crime drama", 3, 1990, 2000, 150, 7.5,
                List.of("Crime", "Drama"), List.of("Comedy"), List.of("Al Pacino"));

        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(jdbcTemplate.query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class)))
                .thenReturn(List.of());

        service.search(request);

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("m.start_year >= :fromYear")
                .contains("m.start_year <= :toYear")
                .contains("m.runtime_minutes <= :runtimeMax")
                .contains("m.rating >= :minRating")
                .contains("g = ANY(m.genres)")
                .contains("actorPattern0");

        MapSqlParameterSource params = (MapSqlParameterSource) paramsCaptor.getValue();
        assertThat(params.getValue("fromYear")).isEqualTo(1990);
        assertThat(params.getValue("toYear")).isEqualTo(2000);
        assertThat(params.getValue("runtimeMax")).isEqualTo(150);
        assertThat(params.getValue("minRating")).isEqualTo(7.5);
        assertThat(params.getValue("incGenres")).isEqualTo("Crime,Drama");
        assertThat(params.getValue("excGenres")).isEqualTo("Comedy");
        assertThat(params.getValue("actorPattern0")).isEqualTo("%alpacino%");
        assertThat(params.getValue("limit")).isEqualTo(3);
    }

    @Test
    void searchWithoutOptionalFiltersOmitsTheirBindParameters() {
        MovieSearchRequest request = new MovieSearchRequest("any movie", null, null, null, null, null,
                List.of(), List.of(), List.of());

        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f});
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(jdbcTemplate.query(anyString(), paramsCaptor.capture(), any(RowMapper.class)))
                .thenReturn(List.of());

        service.search(request);

        MapSqlParameterSource params = (MapSqlParameterSource) paramsCaptor.getValue();
        assertThat(params.hasValue("fromYear")).isFalse();
        assertThat(params.hasValue("toYear")).isFalse();
        assertThat(params.hasValue("runtimeMax")).isFalse();
        assertThat(params.hasValue("minRating")).isFalse();
        assertThat(params.hasValue("incGenres")).isFalse();
        assertThat(params.hasValue("excGenres")).isFalse();
        assertThat(params.hasValue("actorPattern0")).isFalse();
        // no configured limit falls back to properties.maxResults()
        assertThat(params.getValue("limit")).isEqualTo(15);
    }

    @Test
    void fetchByTconstForcesSimilarityToOneRegardlessOfColumn() throws SQLException {
        ArgumentCaptor<RowMapper<MovieContext>> captor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), captor.capture()))
                .thenAnswer(invocation -> {
                    RowMapper<MovieContext> mapper = captor.getValue();
                    ResultSet rs = mockRow("tt9", "Some Movie", 2010, 6.0, 100, null,
                            new String[]{"Action"}, null, "movie", 90, false, null);
                    return List.of(mapper.mapRow(rs, 1));
                });

        Optional<MovieContext> result = service.fetchByTconst("tt9");

        assertThat(result).isPresent();
        assertThat(result.get().similarity()).isEqualTo(1.0d);
    }

    @Test
    void fetchByTconstReturnsEmptyWhenNoRowMatches() {
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        Optional<MovieContext> result = service.fetchByTconst("ttMissing");

        assertThat(result).isEmpty();
    }

    @Test
    void lookupByTitleReturnsEmptyForBlankTitleWithoutQueryingDatabase() {
        Optional<MovieContext> result = service.lookupByTitle("   ", null);

        assertThat(result).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void lookupByTitleAddsYearFilterAndOrderingWhenYearProvided() {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(SqlParameterSource.class);
        when(jdbcTemplate.query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class)))
                .thenReturn(List.of());

        service.lookupByTitle("Heat", 1995);

        assertThat(sqlCaptor.getValue()).contains("AND m.start_year = :year");
        MapSqlParameterSource params = (MapSqlParameterSource) paramsCaptor.getValue();
        assertThat(params.getValue("title")).isEqualTo("Heat");
        assertThat(params.getValue("year")).isEqualTo(1995);
    }

    @Test
    void lookupByTitleOmitsYearFilterWhenYearNotProvided() {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.query(sqlCaptor.capture(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        service.lookupByTitle("Heat", null);

        assertThat(sqlCaptor.getValue()).doesNotContain(":year");
    }

    @Test
    void cacheEvictsOldestEntryOnceCapacityExceeded() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.2f});
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        MovieSearchRequest first = request("query-0");
        service.search(first);
        for (int i = 1; i <= 500; i++) {
            service.search(request("query-" + i));
        }
        // 501 distinct entries inserted against a 500-entry cache: the first one must have been evicted.
        service.search(first);

        verify(embeddingModel, times(502)).embed(anyString());
    }

    @Test
    void cacheServesRecentEntryWithoutReEmbedding() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.2f});
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        MovieSearchRequest req = request("stable query");
        service.search(req);
        service.search(req);

        verify(embeddingModel, times(1)).embed(eq("stable query"));
        verify(jdbcTemplate, times(1)).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
    }

    private MovieSearchRequest request(String query) {
        return new MovieSearchRequest(query, 5, null, null, null, null, List.of(), List.of(), List.of());
    }
}
