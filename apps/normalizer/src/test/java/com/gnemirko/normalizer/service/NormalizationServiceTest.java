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

    @Test
    void translatesColloquialEveningPrompt() {
        client.enqueue("{\"language\":\"ru\",\"confidence\":0.73}");
        client.enqueue("A movie for the evening");
        NormalizationRequest request = new NormalizationRequest();
        request.setText("Фильм на вечер");
        request.setTargetLanguage("en");

        NormalizationResponse response = service.normalize(request);

        assertThat(response.translationApplied()).isTrue();
        assertThat(response.normalizedText()).isEqualTo("A movie for the evening");
        assertThat(client.callCount()).isEqualTo(2);
        assertThat(client.lastCall().prompt()).contains("Фильм на вечер");
    }

    @Test
    void keepsMixedLanguageRequestWhenDetectedEnglish() {
        client.enqueue("{\"language\":\"en\",\"confidence\":0.91}");
        NormalizationRequest request = new NormalizationRequest();
        request.setText("Need фильм for evening");
        request.setTargetLanguage("en");

        NormalizationResponse response = service.normalize(request);

        assertThat(response.translationApplied()).isFalse();
        assertThat(response.normalizedText()).isEqualTo("Need фильм for evening");
        assertThat(client.callCount()).isEqualTo(1);
    }

    @Test
    void fallsBackToUnknownWhenDetectionCallThrows() {
        client.enqueueError(new RuntimeException("ollama unreachable"));
        NormalizationRequest request = new NormalizationRequest();
        request.setText("bonjour");
        request.setTargetLanguage("en");

        NormalizationResponse response = service.normalize(request);

        assertThat(response.detectedLanguage()).isEqualTo("unknown");
        assertThat(response.translationApplied()).isFalse();
        assertThat(response.normalizedText()).isEqualTo("bonjour");
        assertThat(response.notes()).isEqualTo("Unable to determine language");
        assertThat(client.callCount()).isEqualTo(1);
    }

    @Test
    void fallsBackToUnknownWhenDetectionResponseIsNotJson() {
        client.enqueue("I'm sorry, I cannot help with that.");
        NormalizationRequest request = new NormalizationRequest();
        request.setText("hello");
        request.setTargetLanguage("en");

        NormalizationResponse response = service.normalize(request);

        assertThat(response.detectedLanguage()).isEqualTo("unknown");
        assertThat(response.translationApplied()).isFalse();
    }

    @Test
    void detectionResponseMissingOptionalFieldsStillParses() {
        client.enqueue("{\"language\":\"FR\"}");
        NormalizationRequest request = new NormalizationRequest();
        request.setText("bonjour le monde");
        request.setTargetLanguage("en");
        client.enqueue("Hello world");

        NormalizationResponse response = service.normalize(request);

        assertThat(response.detectedLanguage()).isEqualTo("fr");
        assertThat(response.translationApplied()).isTrue();
    }

    @Test
    void fallsBackToOriginalTextWhenTranslationCallThrows() {
        client.enqueue("{\"language\":\"ru\",\"confidence\":0.95}");
        client.enqueueError(new RuntimeException("translation backend down"));
        NormalizationRequest request = new NormalizationRequest();
        request.setText("привет мир");
        request.setTargetLanguage("en");

        NormalizationResponse response = service.normalize(request);

        assertThat(response.translationApplied()).isTrue();
        assertThat(response.normalizedText()).isEqualTo("привет мир");
        assertThat(response.notes()).isEqualTo("Translated from ru to en");
    }

    @Test
    void fallsBackToOriginalTextWhenTranslationResponseIsBlank() {
        client.enqueue("{\"language\":\"ru\",\"confidence\":0.95}");
        client.enqueue("   ");
        NormalizationRequest request = new NormalizationRequest();
        request.setText("привет мир");
        request.setTargetLanguage("en");

        NormalizationResponse response = service.normalize(request);

        assertThat(response.normalizedText()).isEqualTo("привет мир");
    }

    @Test
    void translatesRegardlessOfLowDetectionConfidenceAsLongAsLanguageDiffers() {
        client.enqueue("{\"language\":\"de\",\"confidence\":0.05}");
        client.enqueue("Translated anyway");
        NormalizationRequest request = new NormalizationRequest();
        request.setText("guten tag");
        request.setTargetLanguage("en");

        NormalizationResponse response = service.normalize(request);

        assertThat(response.translationApplied()).isTrue();
        assertThat(response.normalizedText()).isEqualTo("Translated anyway");
    }

    private static final class DummyCompletionClient implements CompletionClient {
        private final Deque<Object> responses = new ArrayDeque<>();
        private final Deque<Call> calls = new ArrayDeque<>();

        void enqueue(String response) {
            responses.addLast(response);
        }

        void enqueueError(RuntimeException error) {
            responses.addLast(error);
        }

        @Override
        public String complete(String model, String prompt) {
            calls.addLast(new Call(model, prompt));
            Object next = responses.pollFirst();
            if (next instanceof RuntimeException error) {
                throw error;
            }
            return (String) next;
        }

        int callCount() {
            return calls.size();
        }

        Call lastCall() {
            return calls.peekLast();
        }

        private record Call(String model, String prompt) {
        }
    }
}
