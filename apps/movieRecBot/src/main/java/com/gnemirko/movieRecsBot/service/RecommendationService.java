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
import static com.gnemirko.movieRecsBot.service.TelegramMessageFormatter.unescapeBasicHtml;

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
        Reply englishReply = dialogPolicy.recommendNow(chatId, normalizedUserText)
                ? generateRecommendation(chatId, context, normalizedUserText, userPrompt)
                : handleClarifyingStage(chatId, context, normalizedUserText, userPrompt);

        userContextService.append(chatId, "User: " + normalizedUserText);
        userContextService.append(chatId, "Bot: " + htmlToPlain(englishReply.text()));

        String localized = textNormalizer.translateFromEnglish(englishReply.text(), language);
        if (!englishReply.includeReminder()) {
            return localized;
        }
        String localizedReminder = translateReminder(language);
        return appendOpinionReminder(localized, localizedReminder);
    }

    private Reply handleClarifyingStage(long chatId,
                                        PromptContext context,
                                        String normalizedUserText,
                                        String userPrompt) {
        String systemPrompt = promptBuilder.buildQuestionSystemPrompt(context.language(), normalizedUserText);
        String response = recommendationModelClient.call(systemPrompt, userPrompt);
        String stripped = stripCodeFence(response).trim();
        boolean looksLikeRecommendation = RecommendationMessageClassifier.looksLikeRecommendation(stripped);
        if ("__RECOMMEND__".equalsIgnoreCase(stripped) || looksLikeRecommendation) {
            dialogPolicy.reset(chatId);
            return generateRecommendation(chatId, context, normalizedUserText, userPrompt);
        }
        dialogPolicy.countClarifying(chatId);
        return new Reply(sanitize(stripped), false);
    }

    private Reply generateRecommendation(long chatId,
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
        String rendered = unescapeBasicHtml(formatRecommendation(raw, parsed));
        dialogPolicy.reset(chatId);
        return new Reply(rendered, true);
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

    private String appendOpinionReminder(String text, String reminderText) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String reminder = "<i>" + escapeHtml(reminderText) + "</i>";
        return text + "\n\n" + reminder;
    }

    private String translateReminder(UserLanguage language) {
        if (language == null || !language.requiresTranslation()) {
            return REMINDER_TEMPLATE;
        }
        String translated = textNormalizer.translateFromEnglish(REMINDER_TEMPLATE, language);
        return translated == null || translated.isBlank() ? REMINDER_TEMPLATE : translated;
    }

    private record Reply(String text, boolean includeReminder) {
    }
}
