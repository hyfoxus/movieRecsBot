package com.gnemirko.mcpmovie.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.mcpmovie.config.MovieMcpProperties;
import com.gnemirko.mcpmovie.model.MovieContext;
import com.gnemirko.mcpmovie.model.MovieSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieSearchServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Mock
    private EmbeddingModel embeddingModel;

    private MovieSearchService service;

    @BeforeEach
    void setUp() {
        MovieMcpProperties properties = new MovieMcpProperties("name", "1.0.0", "desc", 15, 300);
        service = new MovieSearchService(jdbcTemplate, embeddingModel, new ObjectMapper(), properties);
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});
        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
    }

    private MovieSearchRequest request(String query) {
        return new MovieSearchRequest(query, 5, null, null, null, null, List.of(), List.of(), List.of());
    }

    @Test
    void repeatedIdenticalSearchIsServedFromCache() {
        MovieSearchRequest req = request("cozy noir movie");

        List<MovieContext> first = service.search(req);
        List<MovieContext> second = service.search(req);

        assertThat(first).isEqualTo(second);
        verify(embeddingModel, times(1)).embed(anyString());
        verify(jdbcTemplate, times(1)).query(anyString(), any(SqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void differentQueriesAreNotCachedTogether() {
        service.search(request("cozy noir movie"));
        service.search(request("upbeat comedy"));

        verify(embeddingModel, times(2)).embed(anyString());
    }
}
