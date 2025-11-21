package com.gnemirko.normalizer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.normalizer.config.NormalizerOllamaProperties;
import com.gnemirko.normalizer.dto.DetectedLanguage;
import com.gnemirko.normalizer.dto.NormalizationRequest;
import com.gnemirko.normalizer.dto.NormalizationResponse;
import com.gnemirko.normalizer.ollama.CompletionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NormalizationService {

    private static final String UNKNOWN = "unknown";

    private final CompletionClient completionClient;
    private final NormalizerOllamaProperties properties;
    private final ObjectMapper objectMapper;

    public NormalizationResponse normalize(NormalizationRequest request) {
        String originalText = request.getText();
        DetectedLanguage detected = detectLanguage(originalText);
        String targetLanguage = request.getTargetLanguage().toLowerCase();

        boolean translationNeeded = needsTranslation(detected.code(), targetLanguage);
        String normalized = originalText;
        String notes = detected.code();
        if (translationNeeded) {
            normalized = translateText(originalText, detected.code(), targetLanguage);
            notes = "Translated from " + detected.code() + " to " + targetLanguage;
        } else {
            notes = detected.code().equals(targetLanguage)
                    ? "Already in target language"
                    : "Unable to determine language";
        }

        return NormalizationResponse.builder()
                .originalText(originalText)
                .normalizedText(normalized)
                .detectedLanguage(detected.code())
                .translationApplied(translationNeeded)
                .notes(notes)
                .build();
    }

    private DetectedLanguage detectLanguage(String text) {
        try {
            String prompt = PromptFactory.languageDetectionPrompt(text);
            String raw = completionClient.complete(properties.getDetectionModel(), prompt);
            String jsonPayload = extractJson(raw);
            if (jsonPayload == null || jsonPayload.isBlank()) {
                return DetectedLanguage.unknown();
            }
            JsonNode node = objectMapper.readTree(jsonPayload);
            String code = value(node, "language", UNKNOWN).toLowerCase();
            double confidence = node.has("confidence") ? node.get("confidence").asDouble() : 0.0;
            String label = value(node, "label", "");
            if (code.isBlank()) {
                code = UNKNOWN;
            }
            return DetectedLanguage.builder()
                    .code(code)
                    .confidence(confidence)
                    .label(label)
                    .build();
        } catch (Exception ex) {
            log.warn("Failed to detect language: {}", ex.getMessage());
            return DetectedLanguage.unknown();
        }
    }

    private String translateText(String text, String sourceLanguage, String targetLanguage) {
        try {
            String prompt = PromptFactory.translationPrompt(text, sourceLanguage, targetLanguage);
            String translated = completionClient.complete(properties.getTranslationModel(), prompt);
            if (translated == null || translated.isBlank()) {
                return text;
            }
            return translated.trim();
        } catch (Exception ex) {
            log.warn("Failed to translate text: {}", ex.getMessage());
            return text;
        }
    }

    private boolean needsTranslation(String detected, String target) {
        if (target == null || target.isBlank()) {
            return false;
        }
        if (detected == null || detected.isBlank() || UNKNOWN.equals(detected)) {
            return false;
        }
        return !detected.equalsIgnoreCase(target);
    }

    private String extractJson(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    private String value(JsonNode node, String field, String defaultValue) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return defaultValue;
        }
        return node.get(field).asText(defaultValue);
    }
}
