package com.gnemirko.movieRecsBot.repository;

import com.gnemirko.movieRecsBot.entity.RecommendationTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendationTaskRepository extends JpaRepository<RecommendationTask, Long> {
    List<RecommendationTask> findTop20ByChatIdOrderByCreatedAtDesc(Long chatId);
}