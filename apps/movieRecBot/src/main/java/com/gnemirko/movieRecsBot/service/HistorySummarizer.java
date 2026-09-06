package com.gnemirko.movieRecsBot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class HistorySummarizer {

    private final ChatClient chatClient;
    private final HistorySummaryPromptProperties properties;

    /**
     * Compresses a batch of raw "User:"/"Bot:" lines into one durable-preferences sentence.
     * Returns {@code null} on any failure — compaction is best-effort and non-fatal.
     */
    public String summarize(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        try {
            String userPrompt = "Conversation turns to compress:\n" + String.join("\n", lines);
            String response = chatClient
                    .prompt()
                    .system(properties.getSystemPrompt())
                    .user(userPrompt)
                    .call()
                    .content();
            String trimmed = response == null ? "" : response.trim();
            return trimmed.isEmpty() ? null : trimmed;
        } catch (Exception ex) {
            log.debug("History summarization failed: {}", ex.getMessage());
            return null;
        }
    }
}
