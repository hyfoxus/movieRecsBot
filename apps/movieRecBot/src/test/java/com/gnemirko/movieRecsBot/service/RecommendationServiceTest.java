package com.gnemirko.movieRecsBot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.movieRecsBot.entity.UserProfile;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private PromptContextBuilder promptContextBuilder;
    @Mock
    private RecommendationPromptBuilder promptBuilder;
    @Mock
    private RecommendationModelClient recommendationModelClient;
    @Mock
    private RecommendationResponseParser recommendationResponseParser;
    @Mock
    private RecommendationRenderer recommendationRenderer;
    @Mock
    private TextNormalizer textNormalizer;
    @Mock
    private DialogPolicy dialogPolicy;
    @Mock
    private UserContextService userContextService;

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(
                promptContextBuilder,
                promptBuilder,
                recommendationModelClient,
                recommendationResponseParser,
                recommendationRenderer,
                textNormalizer,
                dialogPolicy,
                userContextService
        );
    }

    @Test
    void replyEscalatesFromClarifierToRecommendation() {
        long chatId = 77L;
        UserLanguage language = UserLanguage.fromIsoCode("ru");
        NormalizedInput normalizedInput = new NormalizedInput("movie for the evening", language);
        when(textNormalizer.normalizeToEnglish("Фильм на вечер")).thenReturn(normalizedInput);

        PromptContext context = new PromptContext(new UserProfile(), language, "summary", "history", "context", List.of(), UserIntent.empty());
        when(promptContextBuilder.build(chatId, "movie for the evening", language)).thenReturn(context);

        when(promptBuilder.buildUserPrompt(context, "movie for the evening")).thenReturn("userPrompt");
        when(dialogPolicy.recommendNow(chatId, "movie for the evening")).thenReturn(false);
        when(promptBuilder.buildQuestionSystemPrompt(language, "movie for the evening")).thenReturn("questionSystem");
        when(promptBuilder.buildRecommendationSystemPrompt(language, "movie for the evening")).thenReturn("recommendationSystem");

        when(recommendationModelClient.call("questionSystem", "userPrompt")).thenReturn("__RECOMMEND__");
        when(recommendationModelClient.call("recommendationSystem", "userPrompt")).thenReturn("{\"movies\":[],\"reminder\":\"Share\"}");

        RecommendationResponseParser.ParsedResponse parsedResponse = sampleParsedResponse();
        when(recommendationResponseParser.parse(anyString(), any(), anyString(), any())).thenReturn(parsedResponse);
        when(recommendationRenderer.render(parsedResponse)).thenReturn("<b>List</b>");

        String reply = service.reply(chatId, "Фильм на вечер");

        assertThat(reply).isEqualTo("<b>List</b>\n\n<i>Share</i>");

        verify(recommendationModelClient, times(2)).call(anyString(), eq("userPrompt"));
        verify(recommendationResponseParser).parse(anyString(), any(), anyString(), any());
        verify(recommendationRenderer).render(parsedResponse);
        verify(dialogPolicy, times(2)).reset(chatId);
        verify(userContextService).append(chatId, "User: movie for the evening");
        verify(userContextService).append(chatId, "Bot: List");
        verifyNoMoreInteractions(userContextService);
    }

    private RecommendationResponseParser.ParsedResponse sampleParsedResponse() {
        RecommendationResponseParser parser = new RecommendationResponseParser(new ObjectMapper());
        return parser.parse(
                "{\"intro\":\"\",\"language\":\"ru\",\"movies\":[{\"title\":\"Movie\",\"reason\":\"Because\",\"year\":2020}],\"reminder\":\"Share\"}",
                new UserProfile(),
                "movie for the evening",
                UserLanguage.fromIsoCode("ru")
        );
    }
}
