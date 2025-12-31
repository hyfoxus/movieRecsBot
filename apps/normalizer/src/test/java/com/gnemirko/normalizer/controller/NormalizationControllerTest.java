package com.gnemirko.normalizer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.normalizer.dto.NormalizationRequest;
import com.gnemirko.normalizer.dto.NormalizationResponse;
import com.gnemirko.normalizer.service.NormalizationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NormalizationController.class)
class NormalizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NormalizationService normalizationService;

    @Test
    void normalizeEndpointReturnsResponse() throws Exception {
        NormalizationResponse response = NormalizationResponse.builder()
                .originalText("привет")
                .normalizedText("hello")
                .detectedLanguage("ru")
                .translationApplied(true)
                .notes("Translated")
                .build();
        when(normalizationService.normalize(any(NormalizationRequest.class))).thenReturn(response);

        mockMvc.perform(post("/normalize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"привет","targetLanguage":"en"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));

        verify(normalizationService).normalize(any(NormalizationRequest.class));
    }

    @Test
    void normalizeEndpointValidatesPayload() throws Exception {
        mockMvc.perform(post("/normalize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"","targetLanguage":"english"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(normalizationService);
    }

    @TestConfiguration
    static class MockConfig {
        @Bean
        NormalizationService normalizationService() {
            return Mockito.mock(NormalizationService.class);
        }
    }
}
