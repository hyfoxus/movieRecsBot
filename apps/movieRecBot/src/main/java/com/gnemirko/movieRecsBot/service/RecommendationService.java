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
import com.gnemirko.movieRecsBot.service.recommendation.UserIntent;
import com.gnemirko.movieRecsBot.service.UserLanguage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.UUID;

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

    private final PromptContextBuilder promptContextBuilder;
    private final RecommendationPromptBuilder promptBuilder;
    private final RecommendationModelClient recommendationModelClient;
    private final RecommendationResponseParser responseParser;
    private final RecommendationRenderer recommendationRenderer;
    private final TextNormalizer textNormalizer;
    private final DialogPolicy dialogPolicy;
    private final UserContextService userContextService;
    private final MovieInfoService movieInfoService;

    public String reply(long chatId, String userText) {
        NormalizedInput normalized = textNormalizer.normalizeToEnglish(userText);
        String correlationId = UUID.randomUUID().toString();
        String normalizedUserText = normalized.normalizedText();
        String originalUserText = normalized.originalText();
        UserLanguage language = normalized.language();

        try (var ignored = MDC.putCloseable("correlationId", correlationId)) {
            log.info("recommendation.request.start chatId={} language={} original='{}'", chatId, language.isoCode(), truncate(originalUserText));

            PromptContext context = promptContextBuilder.build(chatId, normalizedUserText, language);
            Reply reply;
            if (shouldHandleInformationIntent(context)) {
                reply = replyWithMovieInfo(chatId, context.userIntent());
                log.debug("recommendation.intent.info correlationId={} title={} year={}", correlationId,
                        context.userIntent() == null ? null : context.userIntent().requestedTitle(),
                        context.userIntent() == null ? null : context.userIntent().requestedYear());
            } else {
                String userPrompt = promptBuilder.buildUserPrompt(context, normalizedUserText);
                boolean skipClarifier = shouldSkipClarifier(context);
                reply = (skipClarifier || dialogPolicy.recommendNow(chatId, normalizedUserText))
                        ? generateRecommendation(chatId, context, normalizedUserText, userPrompt)
                        : handleClarifyingStage(chatId, context, normalizedUserText, userPrompt);
                log.debug("recommendation.intent.recommend correlationId={} skipClarifier={} catalogItems={}",
                        correlationId, skipClarifier, context.catalogItems().size());
            }

            String userHistoryEntry = originalUserText.equalsIgnoreCase(normalizedUserText)
                    ? "User: " + originalUserText
                    : "User: " + originalUserText + " (en: " + normalizedUserText + ")";
            userContextService.append(chatId, userHistoryEntry);
            userContextService.append(chatId, "Bot: " + htmlToPlain(reply.text()));

            String response = appendOpinionReminder(reply.text(), reply.reminder());
            log.info("recommendation.request.complete chatId={} correlationId={} movies={} reminderPresent={}",
                    chatId,
                    correlationId,
                    reply.movieCount(),
                    reply.reminder() != null && !reply.reminder().isBlank());
            return response;
        }
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
        return new Reply(sanitize(stripped), "", 0);
    }

    private Reply generateRecommendation(long chatId,
                                         PromptContext context,
                                         String normalizedUserText,
                                         String userPrompt) {
        UserLanguage language = context.language();
        String systemPrompt = promptBuilder.buildRecommendationSystemPrompt(language, normalizedUserText);
        String raw = recommendationModelClient.call(systemPrompt, userPrompt);
        RecommendationResponseParser.ParsedResponse parsed = responseParser.parse(
                raw,
                context.profile(),
                normalizedUserText,
                language);
        String rendered = unescapeBasicHtml(formatRecommendation(raw, parsed));
        dialogPolicy.reset(chatId);
        return new Reply(rendered, parsed.reminder(), parsed.movies().size());
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
        if (reminderText == null || reminderText.isBlank()) {
            return text;
        }
        String reminder = "<i>" + escapeHtml(reminderText) + "</i>";
        return text + "\n\n" + reminder;
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        int max = 120;
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "…";
    }

    private record Reply(String text, String reminder, int movieCount) {
    }

    private Reply replyWithMovieInfo(long chatId, UserIntent intent) {
        String text = movieInfoService.describeMovie(
                intent == null ? null : intent.requestedTitle(),
                intent == null ? null : intent.requestedYear());
        dialogPolicy.reset(chatId);
        return new Reply(text, "", 0);
    }

    private boolean shouldHandleInformationIntent(PromptContext context) {
        if (context == null || context.userIntent() == null) {
            return false;
        }
        return context.userIntent().isInformationRequest();
    }

    private boolean shouldSkipClarifier(PromptContext context) {
        if (context == null) {
            return false;
        }
        UserIntent intent = context.userIntent();
        return intent != null && intent.isRecommendationRequest() && intent.hasActors();
    }
}
