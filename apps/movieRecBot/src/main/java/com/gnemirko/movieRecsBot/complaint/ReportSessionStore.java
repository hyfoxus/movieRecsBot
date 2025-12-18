package com.gnemirko.movieRecsBot.complaint;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ReportSessionStore {

    private final ConcurrentMap<Long, ReportSession> activeSessions = new ConcurrentHashMap<>();

    public void save(ReportSession session) {
        activeSessions.put(session.chatId(), session);
    }

    public Optional<ReportSession> get(long chatId) {
        return Optional.ofNullable(activeSessions.get(chatId));
    }

    public void clear(long chatId) {
        activeSessions.remove(chatId);
    }
}
