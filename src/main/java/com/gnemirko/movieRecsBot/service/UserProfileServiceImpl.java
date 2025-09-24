package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserProfileRepository repo;

    @Transactional
    public UserProfile getOrCreate(Long chatId) {
        return repo.findById(chatId)
                .orElseGet(() -> repo.save(UserProfile.builder().telegramUserId(chatId).build()));
    }

    @Transactional
    public UserProfile addGenres(Long tgUserId, Collection<String> genres) {
        UserProfile p = getOrCreate(tgUserId);
        p.getLikedGenres().addAll(normalize(genres));
        return p;
    }

    @Transactional
    public UserProfile addActors(Long tgUserId, Collection<String> actors) {
        UserProfile p = getOrCreate(tgUserId);
        p.getLikedActors().addAll(normalize(actors));
        return p;
    }

    @Transactional
    public UserProfile addDirectors(Long tgUserId, Collection<String> directors) {
        UserProfile p = getOrCreate(tgUserId);
        p.getLikedDirectors().addAll(normalize(directors));
        return p;
    }

    @Transactional
    public UserProfile blockTags(Long tgUserId, Collection<String> tags) {
        UserProfile p = getOrCreate(tgUserId);
        p.getBlocked().addAll(normalize(tags));
        return p;
    }

    @Transactional
    public UserProfile unblockTag(Long tgUserId, String tag) {
        String key = normalize(tag);
        UserProfile p = getOrCreate(tgUserId);
        p.getLikedGenres().remove(key);
        p.getLikedActors().remove(key);
        p.getLikedDirectors().remove(key);
        p.getBlocked().remove(key);
        return p;
    }

    @Transactional
    public void reset(Long tgUserId) {
        UserProfile p = getOrCreate(tgUserId);
        p.getLikedGenres().clear();
        p.getLikedActors().clear();
        p.getLikedDirectors().clear();
        p.getBlocked().clear();
    }

    private static Set<String> normalize(Collection<String> in) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : in) {
            String t = normalize(s);
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
    private static String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    @Transactional
    public void reset(long chatId) {
        var u = getOrCreate(chatId);
        u.getLikedGenres().clear();
        u.getLikedActors().clear();
        u.getLikedDirectors().clear();
        u.getBlocked().clear();
    }
}