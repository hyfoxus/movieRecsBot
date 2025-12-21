package com.gnemirko.movieRecsBot.handler;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MenuStateService {
    public enum Await {ADD_GENRE, ADD_ACTOR, ADD_DIRECTOR, ADD_BLOCK, ADD_OPINION, REPORT_COMPLAINT, NONE}

    private final Map<Long, Await> awaiting = new ConcurrentHashMap<>();

    public void setAwait(long chatId, Await state) {
        if (state == null) state = Await.NONE;
        awaiting.put(chatId, state);
    }

    public Await getAwait(long chatId) {
        return awaiting.getOrDefault(chatId, Await.NONE);
    }

    public void clear(long chatId) {
        awaiting.remove(chatId);
    }
}
