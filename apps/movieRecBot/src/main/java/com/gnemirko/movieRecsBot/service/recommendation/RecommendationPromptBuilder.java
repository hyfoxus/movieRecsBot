package com.gnemirko.movieRecsBot.service.recommendation;

import com.gnemirko.movieRecsBot.service.UserLanguage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecommendationPromptBuilder {

    private final RecommendationPromptProperties properties;

    public String buildQuestionSystemPrompt(UserLanguage language, String userText) {
        return baseSystem(language, userText) + "\n\n" + properties.getAskOrRecommend();
    }

    public String buildRecommendationSystemPrompt(UserLanguage language, String userText) {
        return baseSystem(language, userText) + "\n\n" + jsonResponsePrompt(language);
    }

    public String buildUserPrompt(PromptContext context, String userText) {
        StringBuilder builder = new StringBuilder();
        if (!context.history().isBlank()) {
            builder.append("History (truncated):\n")
                    .append(context.history())
                    .append("\n\n");
        }
        if (!context.profileSummary().isBlank()) {
            builder.append("User profile:\n")
                    .append(context.profileSummary())
                    .append("\n\n");
        }
        if (context.movieContext() != null && !context.movieContext().isBlank()) {
            builder.append(context.movieContext().trim()).append("\n\n");
        }
        if (context.userIntent() != null) {
            String summary = context.userIntent().summary();
            if (summary != null && !summary.isBlank()) {
                builder.append("Interpreted intent:\n")
                        .append(summary.trim())
                        .append("\n\n");
            }
        }
        builder.append("User: ").append(userText);
        return builder.toString();
    }

    private String baseSystem(UserLanguage language, String userText) {
        String strictness = language.directive(userText)
                + properties.getLanguageRuleSuffix();
        return properties.getBaseSystem() + "\n\nLANGUAGE RULE:\n" + strictness;
    }

    private String jsonResponsePrompt(UserLanguage language) {
        return properties.getJsonResponseTemplate().formatted(language.isoCode(), properties.getReminder());
    }
}
