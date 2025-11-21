package com.gnemirko.normalizer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.normalizer.config.NormalizerOllamaProperties;
import com.gnemirko.normalizer.dto.NormalizationRequest;
import com.gnemirko.normalizer.dto.NormalizationResponse;
import com.gnemirko.normalizer.ollama.CompletionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizationServiceTest {

    private DummyCompletionClient client;
    private NormalizationService service;

    @BeforeEach
    void setUp() {
        client = new DummyCompletionClient();
        NormalizerOllamaProperties props = new NormalizerOllamaProperties();
        props.setDetectionModel("detect-model");
        props.setTranslationModel("translate-model");
        service = new NormalizationService(client, props, new ObjectMapper());
    }

    @Test
    void translatesWhenLanguagesDiffer() {
        client.enqueue("{\"language\":\"ru\",\"confidence\":0.98}");
        client.enqueue("Hello Brad Pitt");
        NormalizationRequest request = new NormalizationRequest();
        request.setText("привет бред питт");
        request.setTargetLanguage("en");

        NormalizationResponse response = service.normalize(request);

        assertThat(response.translationApplied()).isTrue();
        assertThat(response.detectedLanguage()).isEqualTo("ru");
        assertThat(response.normalizedText()).isEqualTo("Hello Brad Pitt");
    }

    @Test
    void skipsTranslationWhenAlreadyTargetLanguage() {
        client.enqueue("{\"language\":\"en\",\"confidence\":0.90}");
        NormalizationRequest request = new NormalizationRequest();
        request.setText("hello there");
        request.setTargetLanguage("en");

        NormalizationResponse response = service.normalize(request);

        assertThat(response.translationApplied()).isFalse();
        assertThat(response.normalizedText()).isEqualTo("hello there");
    }

    private static final class DummyCompletionClient implements CompletionClient {
        private final Deque<String> responses = new ArrayDeque<>();

        void enqueue(String response) {
            responses.addLast(response);
        }

        @Override
        public String complete(String model, String prompt) {
            return responses.pollFirst();
        }
    }
}
