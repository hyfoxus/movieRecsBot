package com.gnemirko.movieRecsBot.repository;

import com.gnemirko.movieRecsBot.entity.UserMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserMessageRepository extends JpaRepository<UserMessage, Long> {
    List<UserMessage> findTop50ByChatIdOrderByCreatedAtDesc(Long chatId);
}