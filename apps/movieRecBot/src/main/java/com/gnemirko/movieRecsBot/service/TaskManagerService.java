package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.entity.RecommendationTask;
import com.gnemirko.movieRecsBot.repository.RecommendationTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;

import static com.gnemirko.movieRecsBot.entity.RecommendationTask.Status.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskManagerService {

    private final RecommendationService recommendationService;
    private final RecommendationTaskRepository repo;
    private final LlmBulkhead llmBulkhead;
    private final @Qualifier("recExecutor") Executor recExecutor;
    private final TaskNotifier taskNotifier;
    private final DisplayIdGenerator displayIdGenerator;

    @EventListener(ApplicationReadyEvent.class)
    public void resumeQueuedTasks() {
        var pending = repo.findTop100ByStatusInOrderByCreatedAtAsc(List.of(QUEUED, RUNNING));
        if (pending.isEmpty()) {
            return;
        }
        log.info("Resuming {} pending recommendation tasks", pending.size());
        pending.forEach(task -> {
            if (task.getDisplayId() == null || task.getDisplayId().isBlank()) {
                task.setDisplayId(displayIdGenerator.nextId());
            }
            if (task.getStatus() == RUNNING) {
                task.setStatus(QUEUED);
                task.setStartedAt(null);
            }
            repo.save(task);
            dispatch(task.getId());
        });
    }

    public RecommendationTask enqueue(Long chatId, Long userId, String prompt) {
        String displayId = displayIdGenerator.nextId();
        RecommendationTask t = RecommendationTask.builder()
                .displayId(displayId)
                .chatId(chatId)
                .userId(userId)
                .prompt(prompt)
                .status(QUEUED)
                .createdAt(Instant.now())
                .build();
        RecommendationTask saved = repo.save(t);
        log.info("Queued recommendation task {} for chat {}", displayId, chatId);
        dispatch(saved.getId());
        return saved;
    }

    private void dispatch(Long taskId) {
        recExecutor.execute(() -> {
            log.debug("Dispatching recommendation task {}", taskId);
            runTask(taskId);
        });
    }

    private void runTask(Long taskId) {
        RecommendationTask t = repo.findById(taskId).orElse(null);
        if (t == null) {
            log.warn("Task {} not found for processing", taskId);
            return;
        }

        if (t.getStatus() != QUEUED) {
            log.debug("Task {} skipped, status {}", taskId, t.getStatus());
            return;
        }

        String displayId = t.getDisplayId();
        log.info("Starting recommendation task {} ({}) for chat {}", taskId, displayId, t.getChatId());
        t.setStatus(RUNNING);
        t.setStartedAt(Instant.now());
        repo.save(t);

        try {
            llmBulkhead.acquire();
            String text;
            try {
                text = recommendationService.reply(t.getChatId(), t.getPrompt());
            } finally {
                llmBulkhead.release();
            }

            t.setResultText(text);
            t.setStatus(DONE);
            t.setFinishedAt(Instant.now());
            repo.save(t);

            log.info("Recommendation task {} ({}) completed successfully", taskId, displayId);
            taskNotifier.send(SendMessage.builder()
                    .chatId(String.valueOf(t.getChatId()))
                    .text(text)
                    .parseMode("HTML")
                    .disableWebPagePreview(true)
                    .build());

        } catch (Exception e) {
            log.error("Task {} ({}) failed", taskId, displayId, e);
            t.setError(e.getMessage());
            t.setStatus(FAILED);
            t.setFinishedAt(Instant.now());
            repo.save(t);

            log.debug("Recommendation task {} ({}) failed: {}", taskId, displayId, e.getMessage());
            taskNotifier.send(SendMessage.builder()
                    .chatId(String.valueOf(t.getChatId()))
                    .text("💥 Ошибка в рекомендации: " + e.getMessage())
                    .build());
        } finally {
            cleanupTask(t);
        }
    }

    private void cleanupTask(RecommendationTask task) {
        if (task == null) return;
        try {
            repo.deleteById(task.getId());
            log.debug("Removed task {} ({}) from queue", task.getId(), task.getDisplayId());
        } catch (Exception e) {
            log.warn("Failed to remove task {} from queue: {}", task.getId(), e.getMessage());
        }
    }

}
