package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.entity.UserMessage;
import com.gnemirko.movieRecsBot.repository.UserMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserContextService {

    private static final String SUMMARY_PREFIX = "Summary: ";
    private static final int RAW_WINDOW = 30;
    private static final int COMPACT_TRIGGER = 45;

    private final UserMessageRepository repo;
    private final HistorySummarizer historySummarizer;

    public void append(long chatId, String text) {
        saveMessage(chatId, text, Instant.now());
    }

    private void saveMessage(long chatId, String text, Instant at) {
        repo.save(UserMessage.builder()
                .chatId(chatId)
                .text(text.length() > 2000 ? text.substring(0, 2000) : text)
                .createdAt(at)
                .build());
    }

    public String historyAsOneString(long chatId, int maxRecords, int truncateEach) {
        List<UserMessage> summaries = repo.findSummaries(chatId);
        List<UserMessage> raw = repo.findRawDesc(chatId, PageRequest.of(0, maxRecords));

        List<UserMessage> combined = new ArrayList<>(summaries);
        combined.addAll(raw);
        combined.sort(Comparator.comparing(UserMessage::getCreatedAt));

        return combined.stream()
                .map(m -> m.getText().length() > truncateEach ? m.getText().substring(0, truncateEach) + "…" : m.getText())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Compresses conversation turns beyond the raw recency window into one summary row, keeping
     * long-term context available without letting history grow unbounded. Best-effort: any failure
     * (including summarization itself) just skips compaction for this call.
     */
    public void compactIfNeeded(long chatId) {
        try {
            if (repo.countRawByChatId(chatId) <= COMPACT_TRIGGER) {
                return;
            }
            List<UserMessage> allRaw = repo.findRawAscForCompaction(chatId);
            int toSummarizeCount = allRaw.size() - RAW_WINDOW;
            if (toSummarizeCount <= 0) {
                return;
            }
            List<UserMessage> older = allRaw.subList(0, toSummarizeCount);
            List<String> lines = older.stream().map(UserMessage::getText).toList();
            String summary = historySummarizer.summarize(lines);
            if (summary == null || summary.isBlank()) {
                return;
            }
            Instant anchor = older.get(older.size() - 1).getCreatedAt();
            repo.deleteAllInBatch(older);
            saveMessage(chatId, SUMMARY_PREFIX + summary, anchor);
        } catch (Exception ex) {
            log.debug("History compaction skipped for chat {}: {}", chatId, ex.getMessage());
        }
    }
}
