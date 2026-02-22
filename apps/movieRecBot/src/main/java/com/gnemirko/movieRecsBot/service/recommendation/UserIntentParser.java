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
        IntentClassification classification = classifyIntent(trimmed, profileSummary, language);
        if (classification.isInformationIntent()) {
            UserIntent infoIntent = classification.toInformationIntent();
            logParsedIntent(trimmed, infoIntent, classification.reasoning());
            return infoIntent;
        }
        return extractRecommendationIntent(trimmed, profileSummary, language, classification);
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

    private void logParsedIntent(String userText, UserIntent intent, List<String> explanations) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
                "Parsed intent for '{}': type={}, title='{}', year={}, actors={}, includeGenres={}, excludeGenres={}, descriptors={}, runtime={}, summary='{}', explanations={}",
                sanitizeForLog(userText),
                intent.intentType(),
                sanitizeForLog(intent.requestedTitle()),
                intent.requestedYear(),
                intent.actorNames(),
                intent.includeGenres(),
                intent.excludeGenres(),
                intent.descriptors(),
                intent.runtimeMinutes(),
                sanitizeForLog(intent.summary()),
                explanations == null || explanations.isEmpty() ? "<none>" : explanations
        );
    }

    private String sanitizeForLog(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IntentPayload(
            @JsonProperty("actors") List<String> actors,
            @JsonProperty("includeGenres") List<String> includeGenres,
            @JsonProperty("excludeGenres") List<String> excludeGenres,
            @JsonProperty("descriptors") List<String> descriptors,
            @JsonProperty("runtimeMinutes") Integer runtimeMinutes,
            @JsonProperty("rewrittenQuery") String rewrittenQuery,
            @JsonProperty("summary") String summary,
            @JsonProperty("intentType") String intentType,
            @JsonProperty("requestedTitle") String requestedTitle,
            @JsonProperty("reasoning") List<String> explanations
    ) {
        UserIntent toDomain() {
            return new UserIntent(
                    sanitizeList(actors),
                    sanitizeList(includeGenres),
                    sanitizeList(excludeGenres),
                    sanitizeList(descriptors),
                    runtimeMinutes,
                    safeTrim(rewrittenQuery),
                    safeTrim(summary),
                    IntentType.fromString(intentType),
                    safeTrim(requestedTitle),
                    null
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

    private IntentClassification classifyIntent(String userText,
                                                String profileSummary,
                                                UserLanguage language) {
        String prompt = buildClassificationPrompt(userText, profileSummary, language);
        try {
            String response = chatClient
                    .prompt()
                    .system(properties.getClassificationPrompt())
                    .user(prompt)
                    .call()
                    .content();
            log.debug("Intent classifier raw response: {}", sanitizeForLog(response));
            String clean = stripCodeFence(response);
            IntentClassificationPayload payload = objectMapper.readValue(clean, IntentClassificationPayload.class);
            IntentClassification classification = payload.toDomain();
            log.debug(
                    "Intent classifier parsed '{}' as type={}, title='{}', year={}, summary='{}', reasoning={}",
                    sanitizeForLog(userText),
                    classification.intentType(),
                    sanitizeForLog(classification.requestedTitle()),
                    classification.requestedYear(),
                    sanitizeForLog(classification.summary()),
                    classification.reasoning() == null || classification.reasoning().isEmpty() ? "<none>" : classification.reasoning()
            );
            return classification;
        } catch (Exception ex) {
            log.debug("Intent classification failed for '{}': {}", sanitizeForLog(userText), ex.getMessage());
            return IntentClassification.recommendationFallback();
        }
    }

    private String buildClassificationPrompt(String userText,
                                             String profileSummary,
                                             UserLanguage language) {
        StringBuilder builder = new StringBuilder();
        builder.append("Language: ").append(language == null ? "en" : language.isoCode()).append("\n");
        builder.append("User request:\n").append(userText).append("\n");
        if (profileSummary != null && !profileSummary.isBlank()) {
            builder.append("Profile summary:\n").append(profileSummary.trim()).append("\n");
        }
        builder.append("Classify whether user wants recommendations or information about a specific movie.");
        return builder.toString();
    }

    private UserIntent extractRecommendationIntent(String userText,
                                                   String profileSummary,
                                                   UserLanguage language,
                                                   IntentClassification classification) {
        String userPrompt = buildUserPrompt(userText, profileSummary, language);
        try {
            String response = chatClient
                    .prompt()
                    .system(properties.getSystemPrompt())
                    .user(userPrompt)
                    .call()
                    .content();
            log.debug("Intent parser raw response: {}", sanitizeForLog(response));
            String clean = stripCodeFence(response);
            IntentPayload payload = objectMapper.readValue(clean, IntentPayload.class);
            UserIntent intent = payload.toDomain();
            logParsedIntent(userText, intent, payload.explanations());
            return intent;
        } catch (Exception ex) {
            log.debug("Failed to parse recommendation intent for '{}': {}", sanitizeForLog(userText), ex.getMessage());
            if (classification != null && classification.hasSummary()) {
                UserIntent fallback = classification.toRecommendationIntent();
                logParsedIntent(userText, fallback, classification.reasoning());
                return fallback;
            }
            return UserIntent.empty();
        }
    }

    private record IntentClassification(IntentType intentType,
                                        String requestedTitle,
                                        Integer requestedYear,
                                        String summary,
                                        List<String> reasoning) {
        boolean isInformationIntent() {
            return intentType == IntentType.INFORMATION && requestedTitle != null && !requestedTitle.isBlank();
        }

        boolean hasSummary() {
            return summary != null && !summary.isBlank();
        }

        UserIntent toInformationIntent() {
            String safeSummary = summary == null || summary.isBlank()
                    ? "Information request about " + requestedTitle
                    : summary;
            return new UserIntent(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    "",
                    safeSummary,
                    IntentType.INFORMATION,
                    requestedTitle.trim(),
                    requestedYear
            );
        }

        UserIntent toRecommendationIntent() {
            String safeSummary = summary == null ? "" : summary.trim();
            return new UserIntent(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    "",
                    safeSummary,
                    IntentType.RECOMMENDATION,
                    "",
                    null
            );
        }

        static IntentClassification recommendationFallback() {
            return new IntentClassification(IntentType.RECOMMENDATION, "", null, "", List.of());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IntentClassificationPayload(
            @JsonProperty("intentType") String intentType,
            @JsonProperty("requestedTitle") String requestedTitle,
            @JsonProperty("requestedYear") Integer requestedYear,
            @JsonProperty("summary") String summary,
            @JsonProperty("reasoning") List<String> reasoning
    ) {
        IntentClassification toDomain() {
            return new IntentClassification(
                    IntentType.fromString(intentType),
                    safeTrim(requestedTitle),
                    requestedYear,
                    safeTrim(summary),
                    reasoning == null ? List.of() : reasoning.stream()
                            .map(this::safeTrim)
                            .filter(s -> !s.isEmpty())
                            .toList()
            );
        }

        private String safeTrim(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
