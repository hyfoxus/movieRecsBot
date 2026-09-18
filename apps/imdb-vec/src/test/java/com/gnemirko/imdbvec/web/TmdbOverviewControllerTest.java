package com.gnemirko.imdbvec.web;

import com.gnemirko.imdbvec.service.BootstrapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TmdbOverviewController.class)
@TestPropertySource(properties = "app.admin.bootstrap-token=secret-token")
class TmdbOverviewControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private BootstrapService bootstrapService;

    @Test
    void triggerOverviewBackfillAcceptsRequestWithValidTokenAndForwardsParams() throws Exception {
        when(bootstrapService.runTmdbOverviewBackfill(500L, 25)).thenReturn(CompletableFuture.completedFuture(null));

        mockMvc.perform(post("/api/admin/tmdb-overviews")
                        .header("X-Bootstrap-Token", "secret-token")
                        .param("maxUpdates", "500")
                        .param("batchSize", "25"))
                .andExpect(status().isAccepted());

        verify(bootstrapService).runTmdbOverviewBackfill(500L, 25);
    }

    @Test
    void triggerOverviewBackfillWorksWithoutOptionalParams() throws Exception {
        when(bootstrapService.runTmdbOverviewBackfill(null, null)).thenReturn(CompletableFuture.completedFuture(null));

        mockMvc.perform(post("/api/admin/tmdb-overviews").header("X-Bootstrap-Token", "secret-token"))
                .andExpect(status().isAccepted());

        verify(bootstrapService).runTmdbOverviewBackfill(null, null);
    }

    @Test
    void triggerOverviewBackfillRejectsMissingToken() throws Exception {
        mockMvc.perform(post("/api/admin/tmdb-overviews"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bootstrapService);
    }

    @Test
    void triggerOverviewBackfillRejectsWrongToken() throws Exception {
        mockMvc.perform(post("/api/admin/tmdb-overviews").header("X-Bootstrap-Token", "nope"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bootstrapService);
    }
}
