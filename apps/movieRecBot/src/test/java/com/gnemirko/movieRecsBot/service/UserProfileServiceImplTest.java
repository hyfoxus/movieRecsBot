package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

    @Mock
    private UserProfileRepository repo;

    private UserProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserProfileServiceImpl(repo);
    }

    @Test
    void addGenresCreatesProfileWithInitializedCollections() {
        long chatId = 101L;
        when(repo.findById(chatId)).thenReturn(Optional.empty());
        when(repo.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfile profile = service.addGenres(chatId, List.of(" Sci-Fi ", "Sci-Fi"));

        ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
        verify(repo).save(saved.capture());
        assertThat(saved.getValue().getLikedGenres()).containsExactly("sci-fi");

        assertThat(profile.getLikedGenres()).containsExactly("sci-fi");
        assertThat(profile.getLikedActors()).isEmpty();
        assertThat(profile.getLikedDirectors()).isEmpty();
        assertThat(profile.getBlocked()).isEmpty();
    }

    @Test
    void addActorsIgnoresNullCollections() {
        long chatId = 202L;
        UserProfile existing = UserProfile.builder().telegramUserId(chatId).build();
        when(repo.findById(chatId)).thenReturn(Optional.of(existing));

        UserProfile profile = service.addActors(chatId, null);

        assertThat(profile.getLikedActors()).isEmpty();
    }
}
