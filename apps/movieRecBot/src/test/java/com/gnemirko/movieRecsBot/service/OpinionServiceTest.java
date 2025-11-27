package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.handler.MenuStateService;
import com.gnemirko.movieRecsBot.handler.MiniMenu;
import com.gnemirko.movieRecsBot.handler.PromptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class OpinionServiceTest {

    private final RecordingUserProfileService userProfileService = new RecordingUserProfileService();
    private final MiniMenu miniMenu = new MiniMenu();
    private final PromptService promptService = new PromptService(new MenuStateService());

    private OpinionService opinionService;

    @BeforeEach
    void setUp() {
        opinionService = new OpinionService(userProfileService, miniMenu, promptService);
    }

    @Test
    void returnsPromptWhenInputEmpty() {
        OpinionService.OpinionResult result = opinionService.save(1L, "   ");

        assertThat(result.success()).isFalse();
        assertThat(result.message().getText()).contains("Нужно указать фильм");
    }

    @Test
    void savesOpinionWhenStructuredTextProvided() {
        OpinionService.OpinionResult result = opinionService.save(42L, "Inception\nОтлично");

        assertThat(result.success()).isTrue();
        assertThat(result.message().getText()).contains("Inception");
        assertThat(userProfileService.lastUserId).isEqualTo(42L);
        assertThat(userProfileService.lastTitle).isEqualTo("Inception");
        assertThat(userProfileService.lastOpinion).isEqualTo("Отлично");
    }

    private static final class RecordingUserProfileService implements UserProfileService {

        long lastUserId;
        String lastTitle;
        String lastOpinion;

        @Override
        public UserProfile getOrCreate(Long chatId) {
            return UserProfile.builder().telegramUserId(chatId).build();
        }

        @Override
        public UserProfileSnapshot snapshot(Long chatId) {
            return UserProfileSnapshot.from(getOrCreate(chatId));
        }

        @Override
        public UserProfile addGenres(Long tgUserId, Collection<String> genres) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserProfile addActors(Long tgUserId, Collection<String> actors) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserProfile addDirectors(Long tgUserId, Collection<String> directors) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserProfile blockTags(Long tgUserId, Collection<String> tags) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserProfile unblockTag(Long tgUserId, String tag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserProfile addMovieOpinion(Long tgUserId, String movieTitle, String opinion) {
            this.lastUserId = tgUserId;
            this.lastTitle = movieTitle;
            this.lastOpinion = opinion;
            return getOrCreate(tgUserId);
        }

        @Override
        public void reset(long chatId) {
            throw new UnsupportedOperationException();
        }
    }
}
