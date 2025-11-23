package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.handler.DialogPolicy;
import com.gnemirko.movieRecsBot.normalizer.NormalizedInput;
import com.gnemirko.movieRecsBot.normalizer.TextNormalizer;
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
    private final TextNormalizer textNormalizer;
    private final DialogPolicy dialogPolicy;
    private final UserContextService userContextService;

    public String reply(long chatId, String userText) {
        NormalizedInput normalized = textNormalizer.normalizeToEnglish(userText);
        String normalizedUserText = normalized.normalizedText();
        UserLanguage language = normalized.language();

        PromptContext context = promptContextBuilder.build(chatId, normalizedUserText, language);
        String userPrompt = promptBuilder.buildUserPrompt(context, normalizedUserText);
        String englishOutput = dialogPolicy.recommendNow(chatId, normalizedUserText)
                ? generateRecommendation(chatId, context, normalizedUserText, userPrompt)
                : handleClarifyingStage(chatId, context, normalizedUserText, userPrompt);

        userContextService.append(chatId, "User: " + normalizedUserText);
        userContextService.append(chatId, "Bot: " + htmlToPlain(englishOutput));
        return textNormalizer.translateFromEnglish(englishOutput, language);
    }

    private String handleClarifyingStage(long chatId,
                                         PromptContext context,
                                         String normalizedUserText,
                                         String userPrompt) {
        String systemPrompt = promptBuilder.buildQuestionSystemPrompt(context.language(), normalizedUserText);
        String response = recommendationModelClient.call(systemPrompt, userPrompt);
        String stripped = stripCodeFence(response).trim();
        boolean looksLikeRecommendation = RecommendationMessageClassifier.looksLikeRecommendation(stripped);
        if ("__RECOMMEND__".equalsIgnoreCase(stripped) || looksLikeRecommendation) {
            dialogPolicy.reset(chatId);
            return appendOpinionReminder(generateRecommendation(chatId, context, normalizedUserText, userPrompt));
        }
        dialogPolicy.countClarifying(chatId);
        return sanitize(stripped);
    }

    private String generateRecommendation(long chatId,
                                          PromptContext context,
                                          String normalizedUserText,
                                          String userPrompt) {
        String systemPrompt = promptBuilder.buildRecommendationSystemPrompt(context.language(), normalizedUserText);
        String raw = recommendationModelClient.call(systemPrompt, userPrompt);
        RecommendationResponseParser.ParsedResponse parsed = responseParser.parse(
                raw,
                context.profile(),
                normalizedUserText,
                UserLanguage.englishFallback());
        String rendered = formatRecommendation(raw, parsed);
        dialogPolicy.reset(chatId);
        return appendOpinionReminder(rendered);
    }

    private String formatRecommendation(String raw,
                                        RecommendationResponseParser.ParsedResponse parsed) {
        if (parsed.movies().isEmpty()) {
            if (RecommendationMessageClassifier.looksLikeRecommendation(raw)) {
                log.warn("LLM skipped JSON contract but returned formatted recommendations.");
                return sanitizeAllowBasicHtml(raw);
            }
            return sanitize(NO_MATCH_TEMPLATE);
        }
        return recommendationRenderer.render(parsed);
    }

    private String appendOpinionReminder(String text) {
        if (text == null || text.isBlank()) return text;
        String reminder = "<i>" + escapeHtml(REMINDER_TEMPLATE) + "</i>";
        return text + "\n\n" + reminder;
    }
}
