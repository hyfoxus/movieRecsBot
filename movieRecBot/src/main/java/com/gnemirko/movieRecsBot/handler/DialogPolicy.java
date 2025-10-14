package com.gnemirko.movieRecsBot.handler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class DialogPolicy {

    private static final int MAX_CLARIFY_TURNS = 2;

    private final ConcurrentHashMap<Long, Integer> clarifyingTurns = new ConcurrentHashMap<>();

    private static final Set<String> RECOMMEND_NOW_PHRASES = Set.of(
            "дай рекоменда", "предложи фильм", "предложи фильмы",
            "не задавай вопросы", "хочу рекомендации", "порекомендуй",
            "давай фильмы", "give recommendation", "recommend now"
    );

    public void reset(long chatId) { clarifyingTurns.remove(chatId); }

    public boolean recommendNow(long chatId, String userText) {
        String t = userText.toLowerCase();
        if (RECOMMEND_NOW_PHRASES.stream().anyMatch(t::contains)) return true;
        return clarifyingTurns.getOrDefault(chatId, 0) >= MAX_CLARIFY_TURNS;
    }

    public void countClarifying(long chatId) {
        clarifyingTurns.merge(chatId, 1, Integer::sum);
    }
}