package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.entity.MovieOpinion;
import com.gnemirko.movieRecsBot.entity.UserProfile;

import java.util.List;
import java.util.Set;

public record UserProfileSnapshot(Set<String> likedGenres,
                                  Set<String> likedActors,
                                  Set<String> likedDirectors,
                                  Set<String> blocked,
                                  List<MovieOpinion> watchedMovies) {

    public static UserProfileSnapshot from(UserProfile profile) {
        if (profile == null) {
            return new UserProfileSnapshot(Set.of(), Set.of(), Set.of(), Set.of(), List.of());
        }
        return new UserProfileSnapshot(
                Set.copyOf(profile.getLikedGenres()),
                Set.copyOf(profile.getLikedActors()),
                Set.copyOf(profile.getLikedDirectors()),
                Set.copyOf(profile.getBlocked()),
                List.copyOf(profile.getWatchedMovies())
        );
    }
}
