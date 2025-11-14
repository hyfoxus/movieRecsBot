package com.gnemirko.movieRecsBot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
class LanguageDetectionService {

    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.99d;
    private static final int MIN_LENGTH_FOR_CONFIDENCE_FALLBACK = 6;

    private final LanguageDetector lingua;
    private final LlmLanguageClassifier llmClassifier;

    LanguageDetectionService(
            @Value("${app.language.llm.enabled:true}") boolean llmEnabled,
            @Value("${app.language.llm.base-url:http://imdb-ollama:11434}") String llmBaseUrl,
            @Value("${app.language.llm.model:llama3.2}") String llmModel,
            @Value("${app.language.llm.timeout-ms:2000}") long llmTimeoutMs) {
        this.lingua = LanguageDetectorBuilder.fromAllLanguages().build();
        this.llmClassifier = new LlmLanguageClassifier(llmEnabled, llmBaseUrl, llmModel, Duration.ofMillis(llmTimeoutMs));
    }

    UserLanguage detect(String text) {
        if (text == null || text.isBlank()) {
            return UserLanguage.englishFallback();
        }

        var confidenceMap = lingua.computeLanguageConfidenceValues(text);
        if (confidenceMap == null || confidenceMap.isEmpty()) {
            return UserLanguage.englishFallback();
        }

        java.util.List<java.util.Map.Entry<Language, Double>> sorted = confidenceMap.entrySet().stream()
                .sorted(java.util.Map.Entry.<Language, Double>comparingByValue().reversed())
                .toList();
        java.util.Map.Entry<Language, Double> best = sorted.get(0);
        if (isHighConfidence(best, text)) {
            return UserLanguage.fromLanguage(best.getKey());
        }

        Optional<UserLanguage> llmGuess = llmClassifier.classify(text);
        return llmGuess.orElseGet(() -> UserLanguage.fromLanguage(best.getKey()));
    }

    private boolean isHighConfidence(java.util.Map.Entry<Language, Double> best, String text) {
        double value = best.getValue();
        if (value >= HIGH_CONFIDENCE_THRESHOLD) {
            return true;
        }
        if (text.length() < MIN_LENGTH_FOR_CONFIDENCE_FALLBACK) {
            return false;
        }
        Language language = best.getKey();
        return language == Language.ENGLISH || language == Language.RUSSIAN;
    }

    private static final class LlmLanguageClassifier {

        private static final Pattern ISO_PATTERN = Pattern.compile("([A-Za-z]{2,3})");

        private final boolean enabled;
        private final WebClient client;
        private final String model;
        private final Duration timeout;

        private LlmLanguageClassifier(boolean enabled, String baseUrl, String model, Duration timeout) {
            this.enabled = enabled && baseUrl != null && !baseUrl.isBlank();
            this.model = model;
            this.timeout = timeout;
            this.client = this.enabled
                    ? WebClient.builder().baseUrl(baseUrl).build()
                    : null;
        }

        Optional<UserLanguage> classify(String text) {
            if (!enabled || client == null) {
                return Optional.empty();
            }
            try {
                JsonNode response = client.post()
                        .uri("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(ollamaPayload(text))
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .timeout(timeout)
                        .onErrorResume(e -> {
                            log.warn("Language LLM call failed: {}", e.getMessage());
                            return Mono.empty();
                        })
                        .block();

                if (response == null) {
                    return Optional.empty();
                }
                String content = response.path("message").path("content").asText("").trim();
                if (content.isEmpty()) {
                    return Optional.empty();
                }
                Matcher matcher = ISO_PATTERN.matcher(content);
                if (!matcher.find()) {
                    return Optional.empty();
                }
                String iso = matcher.group(1);
                if (iso == null || iso.isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(UserLanguage.fromIsoCode(iso));
            } catch (Exception e) {
                log.warn("Language LLM call failed: {}", e.getMessage());
                return Optional.empty();
            }
        }

        private Object ollamaPayload(String text) {
            return new OllamaChatRequest(model, List.of(
                    new OllamaMessage("system", "You are a language detector. Reply with only the ISO 639-1 code (two letters) of the language used in the user's message. If unavailable, use ISO 639-3."),
                    new OllamaMessage("user", text)
            ));
        }
    }

    private record OllamaChatRequest(String model, List<OllamaMessage> messages) {}

    private record OllamaMessage(String role, String content) {}
}
