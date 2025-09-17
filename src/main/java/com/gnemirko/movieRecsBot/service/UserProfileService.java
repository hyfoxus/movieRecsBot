package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.entity.UserProfile;
import jakarta.transaction.Transactional;

public interface UserProfileService {
    UserProfile getOrCreate(long chatId);
    UserProfile addGenres(long chatId, String... genres);
    UserProfile addActors(long chatId, String... actors);
    UserProfile addDirectors(long chatId, String... directors);
    UserProfile addAnti(long chatId, String... tags);
    UserProfile removeTag(long chatId, String tag);

}
