package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.handler.DialogPolicy;
import com.gnemirko.movieRecsBot.service.recommendation.HelperTextTranslator;
import com.gnemirko.movieRecsBot.service.recommendation.PromptContext;
import com.gnemirko.movieRecsBot.service.recommendation.PromptContextBuilder;
import com.gnemirko.movieRecsBot.service.recommendation.RecommendationModelClient;
import com.gnemirko.movieRecsBot.service.recommendation.RecommendationPromptBuilder;
import com.gnemirko.movieRecsBot.service.recommendation.RecommendationRenderer;
import com.gnemirko.movieRecsBot.service.recommendation.RecommendationResponseParser;
import com.gnemirko.movieRecsBot.service.UserLanguage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import static com.gnemirko.movieRecsBot.service.TelegramMessageFormatter.escapeHtml;
import static com.gnemirko.movieRecsBot.service.TelegramMessageFormatter.htmlToPlain;
import static com.gnemirko.movieRecsBot.service.TelegramMessageFormatter.sanitize;
import static com.gnemirko.movieRecsBot.service.TelegramMessageFormatter.sanitizeAllowBasicHtml;
import static com.gnemirko.movieRecsBot.service.TelegramMessageFormatter.stripCodeFence;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecommendationService {

    private static final String NO_MATCH_TEMPLATE = "I couldn’t find a good match. Share 1–2 favorites and I’ll suggest something similar.";
    private static final String REMINDER_TEMPLATE = "When you watch something, send /watched with your thoughts so I can improve.";

    private final PromptContextBuilder promptContextBuilder;
    private final RecommendationPromptBuilder promptBuilder;
    private final RecommendationModelClient recommendationModelClient;
    private final RecommendationResponseParser responseParser;
    private final RecommendationRenderer recommendationRenderer;
    private final HelperTextTranslator helperTextTranslator;
    private final DialogPolicy dialogPolicy;
    private final UserContextService userContextService;

    public String reply(long chatId, String userText) {
        PromptContext context = promptContextBuilder.build(chatId, userText);
        String userPrompt = promptBuilder.buildUserPrompt(context, userText);
        String output = dialogPolicy.recommendNow(chatId, userText)
                ? generateRecommendation(chatId, context, userText, userPrompt)
                : handleClarifyingStage(chatId, context, userText, userPrompt);

        userContextService.append(chatId, "User: " + userText);
        userContextService.append(chatId, "Bot: " + htmlToPlain(output));
        return output;
    }

    private String handleClarifyingStage(long chatId,
                                         PromptContext context,
                                         String userText,
                                         String userPrompt) {
        String systemPrompt = promptBuilder.buildQuestionSystemPrompt(context.language(), userText);
        String response = recommendationModelClient.call(systemPrompt, userPrompt);
        String stripped = stripCodeFence(response).trim();
        boolean looksLikeRecommendation = RecommendationMessageClassifier.looksLikeRecommendation(stripped);
        if ("__RECOMMEND__".equalsIgnoreCase(stripped) || looksLikeRecommendation) {
            dialogPolicy.reset(chatId);
            return appendOpinionReminder(generateRecommendation(chatId, context, userText, userPrompt), context.language());
        }
        dialogPolicy.countClarifying(chatId);
        return sanitize(stripped);
    }

    private String generateRecommendation(long chatId,
                                          PromptContext context,
                                          String userText,
                                          String userPrompt) {
        String systemPrompt = promptBuilder.buildRecommendationSystemPrompt(context.language(), userText);
        String raw = recommendationModelClient.call(systemPrompt, userPrompt);
        RecommendationResponseParser.ParsedResponse parsed = responseParser.parse(
                raw,
                context.profile(),
                userText,
                context.language());
        String rendered = formatRecommendation(raw, parsed, context.language());
        dialogPolicy.reset(chatId);
        return appendOpinionReminder(rendered, context.language());
    }

    private String formatRecommendation(String raw,
                                        RecommendationResponseParser.ParsedResponse parsed,
                                        UserLanguage language) {
        if (parsed.movies().isEmpty()) {
            if (RecommendationMessageClassifier.looksLikeRecommendation(raw)) {
                log.warn("LLM skipped JSON contract but returned formatted recommendations.");
                return sanitizeAllowBasicHtml(raw);
            }
            String helper = helperTextTranslator.localize(NO_MATCH_TEMPLATE, "noMovies", language);
            return sanitize(helper);
        }
        return recommendationRenderer.render(parsed);
    }

    private String appendOpinionReminder(String text, UserLanguage language) {
        if (text == null || text.isBlank()) return text;
        String reminderText = helperTextTranslator.localize(REMINDER_TEMPLATE, "reminder", language);
        String reminder = "<i>" + escapeHtml(reminderText) + "</i>";
        return text + "\n\n" + reminder;
    }
}
