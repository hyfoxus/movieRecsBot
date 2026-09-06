package com.gnemirko.movieRecsBot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.handler.DialogPolicy;
import com.gnemirko.movieRecsBot.normalizer.NormalizedInput;
import com.gnemirko.movieRecsBot.normalizer.TextNormalizer;
import com.gnemirko.movieRecsBot.service.recommendation.IntentType;
import com.gnemirko.movieRecsBot.service.recommendation.PromptContext;
import com.gnemirko.movieRecsBot.service.recommendation.PromptContextBuilder;
import com.gnemirko.movieRecsBot.service.recommendation.RecommendationModelClient;
import com.gnemirko.movieRecsBot.service.recommendation.RecommendationPromptBuilder;
import com.gnemirko.movieRecsBot.service.recommendation.RecommendationRenderer;
import com.gnemirko.movieRecsBot.service.recommendation.RecommendationResponseParser;
import com.gnemirko.movieRecsBot.service.recommendation.RecommendationSessionStore;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
    @Mock
    private MovieInfoService movieInfoService;
    @Mock
    private RecommendationSessionStore recommendationSessionStore;

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
                userContextService,
                movieInfoService,
                recommendationSessionStore
        );
        lenient().when(promptBuilder.appendExclusions(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void replyEscalatesFromClarifierToRecommendation() {
        long chatId = 77L;
        UserLanguage language = UserLanguage.fromIsoCode("ru");
        NormalizedInput normalizedInput = new NormalizedInput("Фильм на вечер", "movie for the evening", language);
        when(textNormalizer.normalizeToEnglish("Фильм на вечер")).thenReturn(normalizedInput);

        PromptContext context = new PromptContext(new UserProfile(), language, "summary", "history", "context", List.of(), UserIntent.empty());
        when(promptContextBuilder.buildFromPrefetch(any(), eq("movie for the evening"), eq(language))).thenReturn(context);

        when(promptBuilder.buildUserPrompt(context, "movie for the evening")).thenReturn("userPrompt");
        when(dialogPolicy.recommendNow(chatId, "movie for the evening")).thenReturn(false);
        when(promptBuilder.buildQuestionSystemPrompt(language, "movie for the evening")).thenReturn("questionSystem");
        when(promptBuilder.buildRecommendationSystemPrompt(language, "movie for the evening")).thenReturn("recommendationSystem");

        when(recommendationModelClient.call("questionSystem", "userPrompt")).thenReturn("__RECOMMEND__");
        when(recommendationModelClient.call("recommendationSystem", "userPrompt")).thenReturn("{\"movies\":[],\"reminder\":\"Share\"}");

        RecommendationResponseParser.ParsedResponse parsedResponse = sampleParsedResponse();
        when(recommendationResponseParser.parse(anyString(), any(), anyString(), any(), any())).thenReturn(parsedResponse);
        when(recommendationRenderer.render(parsedResponse)).thenReturn("<b>List</b>");

        String reply = service.reply(chatId, "Фильм на вечер");

        assertThat(reply).isEqualTo("<b>List</b>\n\n<i>Share</i>");

        verify(recommendationModelClient, times(2)).call(anyString(), eq("userPrompt"));
        verify(recommendationResponseParser).parse(anyString(), any(), anyString(), any(), any());
        verify(recommendationRenderer).render(parsedResponse);
        verify(dialogPolicy, times(2)).reset(chatId);
        verify(userContextService).append(chatId, "User: Фильм на вечер (en: movie for the evening)");
        verify(userContextService).append(chatId, "Bot: List");
        verify(userContextService).compactIfNeeded(chatId);
        verifyNoMoreInteractions(userContextService);
    }

    @Test
    void replySkipsClarifierWhenActorConstraintsDetected() {
        long chatId = 88L;
        UserLanguage language = UserLanguage.fromIsoCode("ru");
        NormalizedInput normalizedInput = new NormalizedInput("Фильм с Аль Пачино", "movie with al pacino", language);
        when(textNormalizer.normalizeToEnglish("Фильм с Аль Пачино")).thenReturn(normalizedInput);

        UserIntent intent = new UserIntent(List.of("Al Pacino"), List.of(), List.of(), List.of(), null, "", "Movie with Al Pacino", IntentType.RECOMMENDATION, "", null, null);
        PromptContext context = new PromptContext(new UserProfile(), language, "", "", "", List.of(), intent);
        when(promptContextBuilder.buildFromPrefetch(any(), eq("movie with al pacino"), eq(language))).thenReturn(context);

        when(promptBuilder.buildUserPrompt(context, "movie with al pacino")).thenReturn("userPrompt");
        when(promptBuilder.buildRecommendationSystemPrompt(language, "movie with al pacino")).thenReturn("recommendationSystem");
        when(recommendationModelClient.call("recommendationSystem", "userPrompt"))
                .thenReturn("{\"movies\":[],\"reminder\":\"Share\"}");

        RecommendationResponseParser.ParsedResponse parsedResponse = sampleParsedResponse();
        when(recommendationResponseParser.parse(anyString(), any(), anyString(), any(), any())).thenReturn(parsedResponse);
        when(recommendationRenderer.render(parsedResponse)).thenReturn("<b>List</b>");

        String reply = service.reply(chatId, "Фильм с Аль Пачино");

        assertThat(reply).isEqualTo("<b>List</b>\n\n<i>Share</i>");
        verify(promptBuilder, never()).buildQuestionSystemPrompt(any(), anyString());
        verify(recommendationModelClient, times(1)).call(anyString(), eq("userPrompt"));
        verify(dialogPolicy, never()).recommendNow(chatId, "movie with al pacino");
        verify(dialogPolicy).reset(chatId);
        verify(userContextService).append(chatId, "User: Фильм с Аль Пачино (en: movie with al pacino)");
        verify(userContextService).append(chatId, "Bot: List");
        verify(userContextService).compactIfNeeded(chatId);
        verifyNoMoreInteractions(userContextService);
    }

    @Test
    void replyHandlesInformationIntent() {
        long chatId = 55L;
        UserLanguage language = UserLanguage.fromIsoCode("en");
        NormalizedInput normalizedInput = new NormalizedInput("Tell me about Heat", "tell me about Heat", language);
        when(textNormalizer.normalizeToEnglish("Tell me about Heat")).thenReturn(normalizedInput);

        UserIntent infoIntent = new UserIntent(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                "",
                "",
                IntentType.INFORMATION,
                "Heat",
                1995,
                null
        );
        PromptContext context = new PromptContext(new UserProfile(), language, "", "", "", List.of(), infoIntent);
        when(promptContextBuilder.buildFromPrefetch(any(), eq("tell me about Heat"), eq(language))).thenReturn(context);
        when(movieInfoService.describeMovie("Heat", 1995)).thenReturn("<b>Heat</b> (1995)");

        String reply = service.reply(chatId, "Tell me about Heat");

        assertThat(reply).isEqualTo("<b>Heat</b> (1995)");
        verify(movieInfoService).describeMovie("Heat", 1995);
        verifyNoMoreInteractions(promptBuilder, recommendationModelClient, recommendationResponseParser, recommendationRenderer);
        verify(userContextService).append(chatId, "User: Tell me about Heat");
        verify(userContextService).append(chatId, "Bot: Heat (1995)");
        verify(userContextService).compactIfNeeded(chatId);
        verify(dialogPolicy).reset(chatId);
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
