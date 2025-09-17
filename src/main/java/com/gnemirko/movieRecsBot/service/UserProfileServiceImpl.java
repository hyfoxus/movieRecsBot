package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileRepository repo;

    @Transactional
    public UserProfile getOrCreate(long chatId) {
        return repo.findByChatId(chatId).orElseGet(() -> repo.save(
                UserProfile.builder().chatId(chatId).build()
        ));
    }

    @Transactional
    public UserProfile addGenres(long chatId, String... genres) {
        var u = getOrCreate(chatId);
        Arrays.stream(genres).map(this::norm).forEach(u.getPreferredGenres()::add);
        return repo.save(u);
    }

    @Transactional
    public UserProfile addActors(long chatId, String... actors) {
        var u = getOrCreate(chatId);
        Arrays.stream(actors).map(this::norm).forEach(u.getPreferredActors()::add);
        return repo.save(u);
    }

    @Transactional
    public UserProfile addDirectors(long chatId, String... directors) {
        var u = getOrCreate(chatId);
        Arrays.stream(directors).map(this::norm).forEach(u.getPreferredDirectors()::add);
        return repo.save(u);
    }

    @Transactional
    public UserProfile addAnti(long chatId, String... tags) {
        var u = getOrCreate(chatId);
        Arrays.stream(tags).map(this::norm).forEach(u.getAntiPreferences()::add);
        return repo.save(u);
    }

    @Transactional
    public UserProfile removeTag(long chatId, String tag) {
        var u = getOrCreate(chatId);
        var t = norm(tag);
        u.getPreferredGenres().remove(t);
        u.getPreferredActors().remove(t);
        u.getPreferredDirectors().remove(t);
        u.getAntiPreferences().remove(t);
        return repo.save(u);
    }

    private String norm(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ")
                .replace("ё","е")
                .toLowerCase(Locale.ROOT);
    }
}