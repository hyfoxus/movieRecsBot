package com.gnemirko.movieRecsBot.repository;

import com.gnemirko.movieRecsBot.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
}