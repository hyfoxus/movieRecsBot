package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.entity.MovieOpinion;
import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    @Transactional(readOnly = true)
    public UserProfileSnapshot snapshot(Long chatId) {
        return UserProfileSnapshot.from(getOrCreate(chatId));
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

    public UserProfile addMovieOpinion(Long tgUserId, String movieTitle, String opinion) {
        UserProfile p = getOrCreate(tgUserId);
        String title = trimToNull(movieTitle);
        String review = trimToNull(opinion);
        if (title == null && review == null) return p;

        MovieOpinion entry = MovieOpinion.builder()
                .title(title == null ? "неизвестно" : title)
                .opinion(review)
                .recordedAt(Instant.now())
                .build();
        p.getWatchedMovies().add(entry);
        pruneWatched(p.getWatchedMovies());
        return p;
    }


    public void reset(long chatId) {
        var u = getOrCreate(chatId);
        u.getLikedGenres().clear();
        u.getLikedActors().clear();
        u.getLikedDirectors().clear();
        u.getBlocked().clear();
        u.getWatchedMovies().clear();
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

    private static String trimToNull(String in) {
        if (in == null) return null;
        String t = in.trim();
        return t.isEmpty() ? null : t;
    }

    private static void pruneWatched(List<MovieOpinion> watched) {
        if (watched == null) return;
        int max = 50;
        if (watched.size() > max) {
            watched.sort(Comparator.comparing(MovieOpinion::getRecordedAt).reversed());
            while (watched.size() > max) watched.remove(watched.size() - 1);
        }
    }
}
