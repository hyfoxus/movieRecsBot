package com.gnemirko.movieRecsBot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
class LanguageDetectionService {

    private static final String DETECTION_PROMPT = """
            You are a precise language detector.
            Determine the primary language of the user's latest message.
            Respond ONLY with valid JSON:
            {
              "iso": "<ISO 639-1 lowercase code or ISO 639-3 if 2-letter code unavailable>",
              "name": "<English display name of the language>"
            }
            If uncertain, default to English (iso "en").
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    UserLanguage detect(String text) {
        if (text == null || text.isBlank()) {
            return UserLanguage.englishFallback();
        }
        try {
            String raw = chatClient
                    .prompt()
                    .system(DETECTION_PROMPT)
                    .user(text)
                    .call()
                    .content();
            return parse(raw);
        } catch (Exception e) {
            log.warn("Language detection failed: {}", e.getMessage());
            return UserLanguage.englishFallback();
        }
    }

    private UserLanguage parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return UserLanguage.englishFallback();
        }
        String cleaned = TelegramMessageFormatter.stripCodeFence(raw).trim();
        if (cleaned.isEmpty()) {
            return UserLanguage.englishFallback();
        }
        try {
            JsonNode node = objectMapper.readTree(cleaned);
            String iso = node.path("iso").asText("");
            String name = node.path("name").asText("");
            if (iso.isBlank()) {
                return UserLanguage.englishFallback();
            }
            return UserLanguage.fromIsoCode(iso, name);
        } catch (Exception e) {
            log.warn("Unparsable language detector output: {}", e.getMessage());
            return UserLanguage.englishFallback();
        }
    }
}
