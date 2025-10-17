package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.entity.RecommendationTask;
import com.gnemirko.movieRecsBot.repository.RecommendationTaskRepository;
import com.gnemirko.movieRecsBot.webhook.MovieWebhookBot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Instant;

import static com.gnemirko.movieRecsBot.entity.RecommendationTask.Status.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskManagerService {

    private final RecommendationService recommendationService;
    private final RecommendationTaskRepository repo;
    private final MovieWebhookBot sender;
    private final LlmBulkhead llmBulkhead;

    public RecommendationTask enqueue(Long chatId, Long userId, String prompt) {
        RecommendationTask t = RecommendationTask.builder()
                .chatId(chatId)
                .userId(userId)
                .prompt(prompt)
                .status(QUEUED)
                .createdAt(Instant.now())
                .build();
        return repo.save(t);
    }

    @Async("recExecutor")
    public void runAsync(Long taskId) throws TelegramApiException {
        RecommendationTask t = repo.findById(taskId).orElseThrow();
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

            sender.executeAsync(SendMessage.builder()
                    .chatId(String.valueOf(t.getChatId()))
                    .text(text)
                    .parseMode("MarkdownV2")
                    .build(), null);

        } catch (Exception e) {
            log.error("Task {} failed", taskId, e);
            t.setError(e.getMessage());
            t.setStatus(FAILED);
            t.setFinishedAt(Instant.now());
            repo.save(t);

            sender.executeAsync(SendMessage.builder()
                    .chatId(String.valueOf(t.getChatId()))
                    .text("💥 Ошибка в рекомендации: " + e.getMessage())
                    .build(), null);
        }
    }
}