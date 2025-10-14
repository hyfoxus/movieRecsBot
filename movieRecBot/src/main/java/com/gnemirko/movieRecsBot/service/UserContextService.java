package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.entity.UserMessage;
import com.gnemirko.movieRecsBot.repository.UserMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserContextService {

    private final UserMessageRepository repo;

    public void append(long chatId, String text) {
        repo.save(UserMessage.builder()
                .chatId(chatId)
                .text(text.length() > 2000 ? text.substring(0, 2000) : text)
                .createdAt(Instant.now())
                .build());
    }

    public String historyAsOneString(long chatId, int maxRecords, int truncateEach) {
        return repo.findTop50ByChatIdOrderByCreatedAtDesc(chatId).stream()
                .limit(maxRecords)
                .sorted((a,b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .map(m -> m.getText().length() > truncateEach ? m.getText().substring(0, truncateEach) + "…" : m.getText())
                .collect(Collectors.joining("\n"));
    }
}