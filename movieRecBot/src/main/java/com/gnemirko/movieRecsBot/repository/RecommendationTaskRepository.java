package com.gnemirko.movieRecsBot.repository;

import com.gnemirko.movieRecsBot.entity.RecommendationTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecommendationTaskRepository extends JpaRepository<RecommendationTask, Long> {
    List<RecommendationTask> findTop20ByChatIdOrderByCreatedAtDesc(Long chatId);
    boolean existsByDisplayId(String displayId);
    Optional<RecommendationTask> findByDisplayId(String displayId);
    List<RecommendationTask> findByChatIdAndStatusInOrderByCreatedAtAsc(Long chatId, Collection<RecommendationTask.Status> statuses);
    List<RecommendationTask> findTop100ByStatusInOrderByCreatedAtAsc(Collection<RecommendationTask.Status> statuses);
}
