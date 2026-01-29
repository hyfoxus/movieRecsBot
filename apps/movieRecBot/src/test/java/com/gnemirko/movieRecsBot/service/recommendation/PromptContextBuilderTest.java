package com.gnemirko.movieRecsBot.service.recommendation;

import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.mcp.MovieContextService;
import com.gnemirko.movieRecsBot.service.UserContextService;
import com.gnemirko.movieRecsBot.service.UserLanguage;
import com.gnemirko.movieRecsBot.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptContextBuilderTest {

    @Mock
    private UserProfileService userProfileService;
    @Mock
    private UserContextService userContextService;
    @Mock
    private MovieContextService movieContextService;
    @Mock
    private UserIntentParser userIntentParser;

    private PromptContextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new PromptContextBuilder(userProfileService, userContextService, movieContextService, userIntentParser);
    }

    @Test
    void usesOnlyUserMentionedActorsWhenAvailable() {
        long chatId = 10L;
        UserLanguage language = UserLanguage.fromIsoCode("en");
        UserProfile profile = new UserProfile();
        profile.getLikedActors().add("keanu reeves");
        when(userProfileService.getOrCreate(chatId)).thenReturn(profile);
        when(userContextService.historyAsOneString(chatId, 30, 300)).thenReturn("");

        UserIntent intent = new UserIntent(List.of("Al Pacino"), List.of(), List.of(), List.of(), null, "", "");
        when(userIntentParser.parse(eq("movie with al pacino"), anyString(), eq(language))).thenReturn(intent);
        when(movieContextService.buildContextBlock(anyString(), anyString(), eq(profile), eq(language), eq(intent), any()))
                .thenReturn(MovieContextService.ContextBlock.empty());

        builder.build(chatId, "movie with al pacino", language);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(movieContextService).buildContextBlock(anyString(), anyString(), eq(profile), eq(language), eq(intent), captor.capture());
        assertThat(captor.getValue()).containsExactly("Al Pacino");
    }

    @Test
    void fallsBackToProfileActorsWhenUserProvidesNone() {
        long chatId = 11L;
        UserLanguage language = UserLanguage.fromIsoCode("en");
        UserProfile profile = new UserProfile();
        profile.getLikedActors().add("keanu reeves");
        when(userProfileService.getOrCreate(chatId)).thenReturn(profile);
        when(userContextService.historyAsOneString(chatId, 30, 300)).thenReturn("");

        UserIntent emptyIntent = UserIntent.empty();
        when(userIntentParser.parse(eq("cozy evening movie"), anyString(), eq(language))).thenReturn(emptyIntent);
        when(movieContextService.buildContextBlock(anyString(), anyString(), eq(profile), eq(language), eq(emptyIntent), any()))
                .thenReturn(MovieContextService.ContextBlock.empty());

        builder.build(chatId, "cozy evening movie", language);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(movieContextService).buildContextBlock(anyString(), anyString(), eq(profile), eq(language), eq(emptyIntent), captor.capture());
        assertThat(captor.getValue()).containsExactly("keanu reeves");
    }
}
