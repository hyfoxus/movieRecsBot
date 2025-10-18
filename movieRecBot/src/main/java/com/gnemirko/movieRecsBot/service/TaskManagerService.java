package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.entity.RecommendationTask;
import com.gnemirko.movieRecsBot.repository.RecommendationTaskRepository;
import com.gnemirko.movieRecsBot.webhook.MovieWebhookBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

import static com.gnemirko.movieRecsBot.entity.RecommendationTask.Status.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskManagerService {

    private static final char[] DISPLAY_ID_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int DISPLAY_ID_LENGTH = 6;

    private final RecommendationService recommendationService;
    private final RecommendationTaskRepository repo;
    private final MovieWebhookBot sender;
    private final LlmBulkhead llmBulkhead;
    private final @Qualifier("recExecutor") Executor recExecutor;

    @EventListener(ApplicationReadyEvent.class)
    public void resumeQueuedTasks() {
        var pending = repo.findTop100ByStatusInOrderByCreatedAtAsc(List.of(QUEUED, RUNNING));
        if (pending.isEmpty()) {
            return;
        }
        log.info("Resuming {} pending recommendation tasks", pending.size());
        pending.forEach(task -> {
            if (task.getDisplayId() == null || task.getDisplayId().isBlank()) {
                task.setDisplayId(nextDisplayId());
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
        String displayId = nextDisplayId();
        RecommendationTask t = RecommendationTask.builder()
                .displayId(displayId)
                .chatId(chatId)
                .userId(userId)
                .prompt(prompt)
                .status(QUEUED)
                .createdAt(Instant.now())
                .build();
        RecommendationTask saved = repo.save(t);
        dispatch(saved.getId());
        return saved;
    }

    private String nextDisplayId() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = randomDisplayId();
            if (!repo.existsByDisplayId(candidate)) {
                return candidate;
            }
        }
        String candidate;
        do {
            candidate = randomDisplayId();
        } while (repo.existsByDisplayId(candidate));
        return candidate;
    }

    private String randomDisplayId() {
        char[] buf = new char[DISPLAY_ID_LENGTH];
        for (int i = 0; i < DISPLAY_ID_LENGTH; i++) {
            buf[i] = DISPLAY_ID_ALPHABET[ThreadLocalRandom.current().nextInt(DISPLAY_ID_ALPHABET.length)];
        }
        return new String(buf);
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
        log.debug("Starting recommendation task {} ({})", taskId, displayId);
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

            log.debug("Recommendation task {} ({}) completed successfully", taskId, displayId);
            sendMessage(SendMessage.builder()
                    .chatId(String.valueOf(t.getChatId()))
                    .text(text)
                    .parseMode("MarkdownV2")
                    .build());

        } catch (Exception e) {
            log.error("Task {} ({}) failed", taskId, displayId, e);
            t.setError(e.getMessage());
            t.setStatus(FAILED);
            t.setFinishedAt(Instant.now());
            repo.save(t);

            log.debug("Recommendation task {} ({}) failed: {}", taskId, displayId, e.getMessage());
            sendMessage(SendMessage.builder()
                    .chatId(String.valueOf(t.getChatId()))
                    .text("💥 Ошибка в рекомендации: " + e.getMessage())
                    .build());
        } finally {
            cleanupTask(t);
        }
    }

    private void sendMessage(SendMessage message) {
        try {
            sender.execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message: {}", e.getMessage(), e);
            if ("MarkdownV2".equalsIgnoreCase(message.getParseMode())) {
                try {
                    SendMessage fallback = SendMessage.builder()
                            .chatId(message.getChatId())
                            .text(message.getText())
                            .disableWebPagePreview(message.getDisableWebPagePreview())
                            .replyMarkup(message.getReplyMarkup())
                            .build();
                    sender.execute(fallback);
                } catch (TelegramApiException ex) {
                    log.error("Fallback send also failed: {}", ex.getMessage(), ex);
                }
            }
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
