package com.gnemirko.imdbvec.repo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.sql.Array;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieJdbcTest {

    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private NamedParameterJdbcTemplate np;

    private MovieJdbc movieJdbc;

    @BeforeEach
    void setUp() {
        movieJdbc = new MovieJdbc(jdbc, np, 250);
    }

    @Test
    void countCandidatesSetsConfiguredEfSearchAndPassesFilterParams() {
        when(np.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class))).thenReturn(7);

        int count = movieJdbc.countCandidates(new String[]{"Drama"}, new String[]{"Horror"}, (short) 2000, (short) 2020, 150, 7.0);

        assertThat(count).isEqualTo(7);
        verify(jdbc).execute("SET hnsw.ef_search = 250");

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(np).queryForObject(anyString(), paramsCaptor.capture(), eq(Integer.class));
        MapSqlParameterSource params = paramsCaptor.getValue();
        assertThat(params.getValue("fromYear")).isEqualTo((short) 2000);
        assertThat(params.getValue("toYear")).isEqualTo((short) 2020);
        assertThat(params.getValue("runtimeMax")).isEqualTo(150);
        assertThat(params.getValue("minRating")).isEqualTo(7.0);
        assertThat((String[]) params.getValue("inc")).containsExactly("Drama");
        assertThat((String[]) params.getValue("exc")).containsExactly("Horror");
    }

    @Test
    void countCandidatesDefaultsNullGenreArraysToEmptyArrays() {
        when(np.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class))).thenReturn(0);

        movieJdbc.countCandidates(null, null, null, null, null, null);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(np).queryForObject(anyString(), paramsCaptor.capture(), eq(Integer.class));
        MapSqlParameterSource params = paramsCaptor.getValue();
        assertThat((String[]) params.getValue("inc")).isEmpty();
        assertThat((String[]) params.getValue("exc")).isEmpty();
        assertThat(params.getValue("fromYear")).isNull();
    }

    @Test
    void topNSetsEfSearchBuildsVectorLiteralAndPassesLimit() {
        when(np.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        movieJdbc.topN(new float[]{1.5f, -2.25f}, new String[]{"Comedy"}, new String[]{}, new String[]{"tom hanks"},
                (short) 1990, (short) 2010, 200, 5.0, 15);

        verify(jdbc).execute("SET hnsw.ef_search = 250");

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(np).query(anyString(), paramsCaptor.capture(), any(RowMapper.class));
        MapSqlParameterSource params = paramsCaptor.getValue();
        assertThat(params.getValue("vec")).isEqualTo("[1.5,-2.25]");
        assertThat(params.getValue("limit")).isEqualTo(15);
        assertThat((String[]) params.getValue("actorNames")).containsExactly("tom hanks");
    }

    @Test
    void topNDefaultsNullActorAndGenreArraysToEmpty() {
        when(np.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        movieJdbc.topN(new float[]{1f}, null, null, null, null, null, null, null, 10);

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(np).query(anyString(), paramsCaptor.capture(), any(RowMapper.class));
        MapSqlParameterSource params = paramsCaptor.getValue();
        assertThat((String[]) params.getValue("inc")).isEmpty();
        assertThat((String[]) params.getValue("exc")).isEmpty();
        assertThat((String[]) params.getValue("actorNames")).isEmpty();
    }

    @Test
    void topNRowMapperMapsAllColumnsIncludingSqlArrays() throws Exception {
        when(np.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        movieJdbc.topN(new float[]{1f}, new String[]{}, new String[]{}, new String[]{}, null, null, null, null, 10);

        ArgumentCaptor<RowMapper<MovieJdbc.RecoRow>> mapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
        verify(np).query(anyString(), any(SqlParameterSource.class), mapperCaptor.capture());
        RowMapper<MovieJdbc.RecoRow> mapper = mapperCaptor.getValue();

        ResultSet rs = mockRow();
        MovieJdbc.RecoRow row = mapper.mapRow(rs, 0);

        assertThat(row.id()).isEqualTo(42L);
        assertThat(row.tconst()).isEqualTo("tt0000042");
        assertThat(row.title()).isEqualTo("Sample");
        assertThat(row.year()).isEqualTo((short) 2005);
        assertThat(row.rating()).isEqualTo(8.1);
        assertThat(row.votes()).isEqualTo(1000);
        assertThat(row.similarity()).isEqualTo(0.87);
        assertThat(row.genres()).containsExactly("Drama", "Comedy");
        assertThat(row.plot()).isEqualTo("A plot.");
        assertThat(row.actorNames()).containsExactly("Actor A", "Actor B");
        assertThat(row.actorIds()).containsExactly("nm001", "nm002");
    }

    @Test
    void topNRowMapperHandlesNullRatingVotesAndArrays() throws Exception {
        when(np.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());
        movieJdbc.topN(new float[]{1f}, new String[]{}, new String[]{}, new String[]{}, null, null, null, null, 10);

        ArgumentCaptor<RowMapper<MovieJdbc.RecoRow>> mapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
        verify(np).query(anyString(), any(SqlParameterSource.class), mapperCaptor.capture());
        RowMapper<MovieJdbc.RecoRow> mapper = mapperCaptor.getValue();

        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("tconst")).thenReturn("tt1");
        when(rs.getString("primary_title")).thenReturn("Title");
        when(rs.getObject("start_year", Short.class)).thenReturn(null);
        when(rs.getObject("rating")).thenReturn(null);
        when(rs.getObject("votes")).thenReturn(null);
        when(rs.getDouble("sim")).thenReturn(0.5);
        when(rs.getArray("genres")).thenReturn(null);
        when(rs.getString("plot")).thenReturn(null);
        when(rs.getArray("actor_names")).thenReturn(null);
        when(rs.getArray("actor_ids")).thenReturn(null);

        MovieJdbc.RecoRow row = mapper.mapRow(rs, 0);

        assertThat(row.rating()).isNull();
        assertThat(row.votes()).isNull();
        assertThat(row.genres()).isNull();
        assertThat(row.actorNames()).isEmpty();
        assertThat(row.actorIds()).isEmpty();
    }

    private ResultSet mockRow() throws Exception {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(42L);
        when(rs.getString("tconst")).thenReturn("tt0000042");
        when(rs.getString("primary_title")).thenReturn("Sample");
        when(rs.getObject("start_year", Short.class)).thenReturn((short) 2005);
        when(rs.getObject("rating")).thenReturn(8.1);
        when(rs.getDouble("rating")).thenReturn(8.1);
        when(rs.getObject("votes")).thenReturn(1000);
        when(rs.getInt("votes")).thenReturn(1000);
        when(rs.getDouble("sim")).thenReturn(0.87);
        when(rs.getString("plot")).thenReturn("A plot.");

        Array genresArray = org.mockito.Mockito.mock(Array.class);
        when(genresArray.getArray()).thenReturn(new String[]{"Drama", "Comedy"});
        when(rs.getArray("genres")).thenReturn(genresArray);

        Array actorNamesArray = org.mockito.Mockito.mock(Array.class);
        when(actorNamesArray.getArray()).thenReturn(new String[]{"Actor A", "Actor B"});
        when(rs.getArray("actor_names")).thenReturn(actorNamesArray);

        Array actorIdsArray = org.mockito.Mockito.mock(Array.class);
        when(actorIdsArray.getArray()).thenReturn(new String[]{"nm001", "nm002"});
        when(rs.getArray("actor_ids")).thenReturn(actorIdsArray);

        return rs;
    }
}
