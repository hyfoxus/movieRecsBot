package com.gnemirko.movieRecsBot.service.recommendation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.movieRecsBot.service.UserLanguage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserIntentParser {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final UserIntentPromptProperties properties;

    public UserIntent parse(String normalizedUserText,
                            String profileSummary,
                            UserLanguage language) {
        String trimmed = normalizedUserText == null ? "" : normalizedUserText.trim();
        if (trimmed.isEmpty()) {
            return UserIntent.empty();
        }
        String userPrompt = buildUserPrompt(trimmed, profileSummary, language);
        try {
            String response = chatClient
                    .prompt()
                    .system(properties.getSystemPrompt())
                    .user(userPrompt)
                    .call()
                    .content();
            String clean = stripCodeFence(response);
            IntentPayload payload = objectMapper.readValue(clean, IntentPayload.class);
            return payload.toDomain();
        } catch (Exception ex) {
            log.debug("Failed to parse user intent: {}", ex.getMessage());
            return UserIntent.empty();
        }
    }

    private String buildUserPrompt(String userText,
                                   String profileSummary,
                                   UserLanguage language) {
        StringBuilder builder = new StringBuilder();
        builder.append("Language: ").append(language == null ? "en" : language.isoCode()).append("\n");
        builder.append("User request:\n").append(userText).append("\n");
        if (profileSummary != null && !profileSummary.isBlank()) {
            builder.append("Profile summary:\n").append(profileSummary.trim()).append("\n");
        }
        builder.append("Extract actors, genres, dislikes, vibe descriptors, runtime cap if mentioned, and a concise summary.");
        return builder.toString();
    }

    private String stripCodeFence(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```")) {
            int idx = text.indexOf('\n');
            if (idx > 0) {
                text = text.substring(idx + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        }
        return text.trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IntentPayload(
            @JsonProperty("actors") List<String> actors,
            @JsonProperty("includeGenres") List<String> includeGenres,
            @JsonProperty("excludeGenres") List<String> excludeGenres,
            @JsonProperty("descriptors") List<String> descriptors,
            @JsonProperty("runtimeMinutes") Integer runtimeMinutes,
            @JsonProperty("rewrittenQuery") String rewrittenQuery,
            @JsonProperty("summary") String summary
    ) {
        UserIntent toDomain() {
            return new UserIntent(
                    sanitizeList(actors),
                    sanitizeList(includeGenres),
                    sanitizeList(excludeGenres),
                    sanitizeList(descriptors),
                    runtimeMinutes,
                    safeTrim(rewrittenQuery),
                    safeTrim(summary)
            );
        }

        private List<String> sanitizeList(List<String> source) {
            if (source == null || source.isEmpty()) {
                return List.of();
            }
            List<String> cleaned = new ArrayList<>();
            for (String entry : source) {
                String trimmed = safeTrim(entry);
                if (!trimmed.isEmpty()) {
                    cleaned.add(trimmed);
                }
            }
            if (cleaned.isEmpty()) {
                return List.of();
            }
            return cleaned.stream()
                    .map(val -> val.length() <= 1 ? val.toUpperCase(Locale.ROOT) : val)
                    .distinct()
                    .collect(Collectors.toList());
        }

        private String safeTrim(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
