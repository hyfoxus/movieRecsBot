package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.entity.RecommendationTask;
import com.gnemirko.movieRecsBot.repository.RecommendationTaskRepository;
import com.gnemirko.movieRecsBot.service.recommendation.RecommendationMovie;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import static com.gnemirko.movieRecsBot.entity.RecommendationTask.Status.*;
import static com.gnemirko.movieRecsBot.service.TelegramMessageFormatter.prepareTelegramHtml;

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
        try (var ignored = MDC.putCloseable("taskDisplayId", displayId == null ? "" : displayId)) {
            log.info("Starting recommendation task {} ({}) for chat {}", taskId, displayId, t.getChatId());
            t.setStatus(RUNNING);
            t.setStartedAt(Instant.now());
            repo.save(t);

            try {
                llmBulkhead.acquire();
                RecommendationService.RecommendationOutcome outcome;
                try {
                    outcome = recommendationService.replyDetailed(t.getChatId(), t.getPrompt());
                } finally {
                    llmBulkhead.release();
                }

                String sanitizedText = prepareTelegramHtml(outcome.text());
                t.setResultText(sanitizedText);
                t.setStatus(DONE);
                t.setFinishedAt(Instant.now());
                repo.save(t);

                log.info("Recommendation task {} ({}) completed successfully", taskId, displayId);
                taskNotifier.send(SendMessage.builder()
                        .chatId(String.valueOf(t.getChatId()))
                        .text(sanitizedText)
                        .parseMode("HTML")
                        .disableWebPagePreview(true)
                        .replyMarkup(buildFeedbackKeyboard(outcome.movies()))
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
                        .text("💥 Something went wrong while preparing your recommendation. Please try again.")
                        .build());
            } finally {
                cleanupTask(t);
            }
        }
    }

    private InlineKeyboardMarkup buildFeedbackKeyboard(List<RecommendationMovie> movies) {
        if (movies == null || movies.isEmpty()) {
            return null;
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        int limit = Math.min(movies.size(), 5);
        for (int i = 0; i < limit; i++) {
            InlineKeyboardButton up = InlineKeyboardButton.builder().text("👍").callbackData("rate:" + i + ":up").build();
            InlineKeyboardButton down = InlineKeyboardButton.builder().text("👎").callbackData("rate:" + i + ":down").build();
            rows.add(List.of(up, down));
        }
        rows.add(List.of(InlineKeyboardButton.builder().text("🔁 Ещё похожие").callbackData("more:go").build()));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
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
