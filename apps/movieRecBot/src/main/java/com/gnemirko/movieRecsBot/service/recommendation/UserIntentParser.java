package com.gnemirko.movieRecsBot.service.recommendation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.movieRecsBot.service.UserLanguage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
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
        if (isFastPathRecommendation(trimmed)) {
            UserIntent fastPath = fastPathIntent();
            logParsedIntent(trimmed, fastPath, List.of("fast-path: matched a known quick-recommend phrase, skipped LLM classification"));
            return fastPath;
        }
        return extractIntent(trimmed, profileSummary, language);
    }

    /**
     * Short, literal phrases that unambiguously mean "just recommend something" — skips the
     * intent-extraction LLM call entirely for the most common low-information requests.
     */
    private static final Set<String> FAST_PATH_PHRASES = Set.of(
            "дай рекомендации", "дай рекомендацию", "порекомендуй что-нибудь", "ещё", "еще", "ещё раз", "еще раз",
            "surprise me", "another one", "one more", "more"
    );

    private boolean isFastPathRecommendation(String trimmed) {
        return FAST_PATH_PHRASES.contains(trimmed.toLowerCase(Locale.ROOT));
    }

    private UserIntent fastPathIntent() {
        return new UserIntent(List.of(), List.of(), List.of(), List.of(), null, "", "", IntentType.RECOMMENDATION, "", null, null);
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
        builder.append("Classify the intent and extract actors, genres, dislikes, vibe descriptors, runtime cap if mentioned, and a concise summary.");
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
                "Parsed intent for '{}': type={}, title='{}', year={}, recentFrom={}, actors={}, includeGenres={}, excludeGenres={}, descriptors={}, runtime={}, summary='{}', explanations={}",
                sanitizeForLog(userText),
                intent.intentType(),
                sanitizeForLog(intent.requestedTitle()),
                intent.requestedYear(),
                intent.releaseYearFrom(),
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

    private UserIntent extractIntent(String userText,
                                     String profileSummary,
                                     UserLanguage language) {
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
            if (intent.intentType() == IntentType.RECOMMENDATION) {
                intent = applyRecency(intent, userText);
            }
            logParsedIntent(userText, intent, payload.explanations());
            return intent;
        } catch (Exception ex) {
            log.debug("Failed to parse intent for '{}': {}", sanitizeForLog(userText), ex.getMessage());
            return UserIntent.empty();
        }
    }

    private UserIntent applyRecency(UserIntent intent, String originalText) {
        if (intent == null) {
            return null;
        }
        Integer desired = intent.releaseYearFrom();
        if (desired == null) {
            desired = inferRecentYear(originalText);
        }
        if (desired == null || Objects.equals(desired, intent.releaseYearFrom())) {
            return intent;
        }
        return new UserIntent(
                intent.actorNames(),
                intent.includeGenres(),
                intent.excludeGenres(),
                intent.descriptors(),
                intent.runtimeMinutes(),
                intent.rewrittenQuery(),
                intent.summary(),
                intent.intentType(),
                intent.requestedTitle(),
                intent.requestedYear(),
                desired
        );
    }

    private Integer inferRecentYear(String userText) {
        if (userText == null) {
            return null;
        }
        String normalized = userText.toLowerCase(Locale.ROOT);
        for (String keyword : RECENT_KEYWORDS) {
            if (normalized.contains(keyword)) {
                int currentYear = Year.now().getValue();
                return Math.max(currentYear - 1, 1888);
            }
        }
        return null;
    }

    private static final List<String> RECENT_KEYWORDS = List.of(
            "recent", "latest", "fresh", "newest", "new ", "нов", "свеж", "последн", "самых свеж", "самые свеж"
    );

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
            @JsonProperty("requestedYear") Integer requestedYear,
            @JsonProperty("releaseYearFrom") Integer releaseYearFrom,
            @JsonProperty("reasoning") List<String> explanations
    ) {
        UserIntent toDomain() {
            String title = safeTrim(requestedTitle);
            if (IntentType.fromString(intentType) == IntentType.INFORMATION && !title.isEmpty()) {
                String safeSummary = summary == null || summary.isBlank()
                        ? "Information request about " + title
                        : safeTrim(summary);
                return new UserIntent(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        "",
                        safeSummary,
                        IntentType.INFORMATION,
                        title,
                        requestedYear,
                        null
                );
            }
            return new UserIntent(
                    sanitizeList(actors),
                    sanitizeList(includeGenres),
                    sanitizeList(excludeGenres),
                    sanitizeList(descriptors),
                    runtimeMinutes,
                    safeTrim(rewrittenQuery),
                    safeTrim(summary),
                    IntentType.RECOMMENDATION,
                    title,
                    requestedYear,
                    releaseYearFrom
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
