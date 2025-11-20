package com.gnemirko.movieRecsBot.service;

import com.gnemirko.movieRecsBot.repository.RecommendationTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
class DisplayIdGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int DISPLAY_ID_LENGTH = 6;
    private final SecureRandom random = new SecureRandom();
    private final RecommendationTaskRepository repository;

    public String nextId() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = randomId();
            if (!repository.existsByDisplayId(candidate)) {
                return candidate;
            }
        }
        String candidate;
        do {
            candidate = randomId();
        } while (repository.existsByDisplayId(candidate));
        return candidate;
    }

    private String randomId() {
        char[] buffer = new char[DISPLAY_ID_LENGTH];
        for (int i = 0; i < DISPLAY_ID_LENGTH; i++) {
            buffer[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(buffer);
    }
}
