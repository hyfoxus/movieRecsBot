package com.gnemirko.movieRecsBot.repository;

import com.gnemirko.movieRecsBot.entity.UserMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserMessageRepository extends JpaRepository<UserMessage, Long> {
    List<UserMessage> findTop50ByChatIdOrderByCreatedAtDesc(Long chatId);

    @Query("SELECT m FROM UserMessage m WHERE m.chatId = :chatId AND m.text LIKE 'Summary:%' ORDER BY m.createdAt ASC")
    List<UserMessage> findSummaries(@Param("chatId") Long chatId);

    @Query("SELECT m FROM UserMessage m WHERE m.chatId = :chatId AND m.text NOT LIKE 'Summary:%' ORDER BY m.createdAt DESC")
    List<UserMessage> findRawDesc(@Param("chatId") Long chatId, Pageable pageable);

    @Query("SELECT COUNT(m) FROM UserMessage m WHERE m.chatId = :chatId AND m.text NOT LIKE 'Summary:%'")
    long countRawByChatId(@Param("chatId") Long chatId);

    @Query("SELECT m FROM UserMessage m WHERE m.chatId = :chatId AND m.text NOT LIKE 'Summary:%' ORDER BY m.createdAt ASC")
    List<UserMessage> findRawAscForCompaction(@Param("chatId") Long chatId);
}
