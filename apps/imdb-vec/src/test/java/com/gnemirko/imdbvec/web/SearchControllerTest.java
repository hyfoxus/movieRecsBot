package com.gnemirko.imdbvec.web;

import com.gnemirko.imdbvec.repo.MovieJdbc;
import com.gnemirko.imdbvec.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private EmbeddingService embeddingService;
    @MockitoBean
    private MovieJdbc movieJdbc;

    @Test
    void searchReturnsMappedResultsIncludingActorsAndMetadata() throws Exception {
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
        MovieJdbc.RecoRow row = new MovieJdbc.RecoRow(
                1L, "tt0000001", "Sample Movie", (short) 2001, 7.5, 100, 0.9,
                new String[]{"Drama"}, "A gripping plot.", List.of("Actor A"), List.of("nm001"));
        when(movieJdbc.topN(any(), any(), any(), any(), any(), any(), any(), any(), eq(5))).thenReturn(List.of(row));

        mockMvc.perform(get("/api/search/knn").param("q", "space opera").param("k", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tconst").value("tt0000001"))
                .andExpect(jsonPath("$[0].title").value("Sample Movie"))
                .andExpect(jsonPath("$[0].year").value(2001))
                .andExpect(jsonPath("$[0].genres[0]").value("Drama"))
                .andExpect(jsonPath("$[0].actors[0].id").value("nm001"))
                .andExpect(jsonPath("$[0].actors[0].name").value("Actor A"))
                .andExpect(jsonPath("$[0].metadata.plot").value("A gripping plot."));
    }

    @Test
    void searchJoinsMultipleQueryTermsWithPeriodBeforeEmbedding() throws Exception {
        when(embeddingService.embed("term one. term two")).thenReturn(new float[]{0.1f});
        when(movieJdbc.topN(any(), any(), any(), any(), any(), any(), any(), any(), eq(10))).thenReturn(List.of());

        mockMvc.perform(get("/api/search/knn").param("q", "term one", "term two"))
                .andExpect(status().isOk());

        verify(embeddingService).embed("term one. term two");
    }

    @Test
    void searchNormalizesActorNamesToLowercaseTrimmedDistinct() throws Exception {
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
        when(movieJdbc.topN(any(), any(), any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/search/knn")
                        .param("q", "query")
                        .param("actors", " Tom Hanks ", "TOM HANKS", "Meryl Streep"))
                .andExpect(status().isOk());

        verify(movieJdbc).topN(any(), any(), any(),
                argThat(actors -> actors != null && actors.length == 2
                        && "tom hanks".equals(actors[0])
                        && "meryl streep".equals(actors[1])),
                any(), any(), any(), any(), anyInt());
    }

    @Test
    void searchOmitsMetadataWhenPlotIsBlank() throws Exception {
        when(embeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
        MovieJdbc.RecoRow row = new MovieJdbc.RecoRow(
                1L, "tt1", "Title", null, null, null, 0.1,
                new String[]{}, "   ", List.of(), List.of());
        when(movieJdbc.topN(any(), any(), any(), any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of(row));

        mockMvc.perform(get("/api/search/knn").param("q", "query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].metadata").doesNotExist());
    }
}
