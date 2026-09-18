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

@WebMvcTest(BootstrapController.class)
@TestPropertySource(properties = "app.admin.bootstrap-token=secret-token")
class BootstrapControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private BootstrapService bootstrapService;

    @Test
    void triggerBootstrapAcceptsRequestWithValidToken() throws Exception {
        when(bootstrapService.runFullBootstrap(true)).thenReturn(CompletableFuture.completedFuture(null));

        mockMvc.perform(post("/api/admin/bootstrap").header("X-Bootstrap-Token", "secret-token"))
                .andExpect(status().isAccepted());

        verify(bootstrapService).runFullBootstrap(true);
    }

    @Test
    void triggerBootstrapRejectsRequestWithMissingToken() throws Exception {
        mockMvc.perform(post("/api/admin/bootstrap"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bootstrapService);
    }

    @Test
    void triggerBootstrapRejectsRequestWithWrongToken() throws Exception {
        mockMvc.perform(post("/api/admin/bootstrap").header("X-Bootstrap-Token", "wrong"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bootstrapService);
    }

    @Test
    void triggerBootstrapHonorsRebuildIndexParam() throws Exception {
        when(bootstrapService.runFullBootstrap(false)).thenReturn(CompletableFuture.completedFuture(null));

        mockMvc.perform(post("/api/admin/bootstrap")
                        .header("X-Bootstrap-Token", "secret-token")
                        .param("rebuildIndex", "false"))
                .andExpect(status().isAccepted());

        verify(bootstrapService).runFullBootstrap(false);
    }
}
