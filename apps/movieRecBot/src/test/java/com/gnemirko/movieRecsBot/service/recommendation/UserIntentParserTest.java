package com.gnemirko.movieRecsBot.service.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.movieRecsBot.service.UserLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserIntentParserTest {

    private final ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);

    private UserIntentParser parser;

    @BeforeEach
    void setUp() {
        parser = new UserIntentParser(chatClient, new ObjectMapper(), new UserIntentPromptProperties());
    }

    private void stubModelResponse(String json) {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content()).thenReturn(json);
    }

    @Test
    void fastPathSkipsTheModelEntirelyForKnownPhrases() {
        UserIntent intent = parser.parse("дай рекомендации", "", UserLanguage.fromIsoCode("ru"));

        assertThat(intent.intentType()).isEqualTo(IntentType.RECOMMENDATION);
        assertThat(intent.actorNames()).isEmpty();
        verifyNoInteractions(chatClient);
    }

    @Test
    void singleModelCallProducesRecommendationIntent() {
        stubModelResponse("""
                {"actors":["Al Pacino"],"includeGenres":["Comedy"],"excludeGenres":[],"descriptors":[],
                "runtimeMinutes":null,"rewrittenQuery":"comedy starring Al Pacino","summary":"Comedy with Al Pacino",
                "intentType":"RECOMMENDATION","requestedTitle":"","requestedYear":null,"releaseYearFrom":null,"reasoning":[]}
                """);

        UserIntent intent = parser.parse("Нужна комедия с Аль Пачино", "", UserLanguage.fromIsoCode("ru"));

        assertThat(intent.intentType()).isEqualTo(IntentType.RECOMMENDATION);
        assertThat(intent.actorNames()).containsExactly("Al Pacino");
        assertThat(intent.includeGenres()).containsExactly("Comedy");
        assertThat(intent.summary()).isEqualTo("Comedy with Al Pacino");
    }

    @Test
    void singleModelCallProducesInformationIntentWithoutSecondCall() {
        stubModelResponse("""
                {"actors":[],"includeGenres":[],"excludeGenres":[],"descriptors":[],"runtimeMinutes":null,
                "rewrittenQuery":"","summary":"Cast list for Heat","intentType":"INFORMATION",
                "requestedTitle":"Heat","requestedYear":1995,"releaseYearFrom":null,"reasoning":[]}
                """);

        UserIntent intent = parser.parse("Кто играл в Heat 1995?", "", UserLanguage.fromIsoCode("en"));

        assertThat(intent.intentType()).isEqualTo(IntentType.INFORMATION);
        assertThat(intent.requestedTitle()).isEqualTo("Heat");
        assertThat(intent.requestedYear()).isEqualTo(1995);
    }

    @Test
    void recencyKeywordFillsReleaseYearFromWhenModelLeavesItNull() {
        stubModelResponse("""
                {"actors":[],"includeGenres":[],"excludeGenres":[],"descriptors":[],"runtimeMinutes":null,
                "rewrittenQuery":"","summary":"Latest movies","intentType":"RECOMMENDATION",
                "requestedTitle":"","requestedYear":null,"releaseYearFrom":null,"reasoning":[]}
                """);

        UserIntent intent = parser.parse("самый свежий фильм", "", UserLanguage.fromIsoCode("ru"));

        assertThat(intent.releaseYearFrom()).isNotNull();
    }

    @Test
    void modelFailureFallsBackToEmptyIntent() {
        when(chatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new RuntimeException("boom"));

        UserIntent intent = parser.parse("something", "", UserLanguage.fromIsoCode("en"));

        assertThat(intent).isEqualTo(UserIntent.empty());
    }
}
