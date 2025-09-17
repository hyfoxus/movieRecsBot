package com.gnemirko.movieRecsBot.repository;

import com.gnemirko.movieRecsBot.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    Optional<UserProfile> findByChatId(Long chatId);
}