package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.entity.UserProfile;

import java.util.Collection;


public interface UserProfileService {
    UserProfile getOrCreate(Long chatId);
    UserProfile addGenres(Long tgUserId, Collection<String> genres);
    UserProfile addActors(Long tgUserId, Collection<String> actors);
    UserProfile addDirectors(Long tgUserId, Collection<String> directors);
    UserProfile blockTags(Long tgUserId, Collection<String> tags);
    UserProfile unblockTag(Long tgUserId, String tag);
    UserProfile addMovieOpinion(Long tgUserId, String movieTitle, String opinion);
    void reset(long chatId);
}
