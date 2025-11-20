package com.gnemirko.movieRecsBot.service.recommendation;

import com.gnemirko.movieRecsBot.service.UserLanguage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.gnemirko.movieRecsBot.service.TelegramMessageFormatter.stripCodeFence;

@Component
@RequiredArgsConstructor
@Slf4j
public class HelperTextTranslator {

    private final RecommendationModelClient modelClient;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String localize(String english, String keyPrefix, UserLanguage language) {
        if (!language.requiresTranslation()) {
            return english;
        }
        String cacheKey = keyPrefix + "|" + language.isoCode();
        return cache.computeIfAbsent(cacheKey, key -> translate(english, language));
    }

    private String translate(String english, UserLanguage language) {
        try {
            String system = "Translate the following helper text into " + language.displayName() + ". Respond with the translation only.";
            return stripCodeFence(modelClient.call(system, english)).trim();
        } catch (Exception ex) {
            log.warn("Failed to translate helper text to {}: {}", language.displayName(), ex.getMessage());
            return english;
        }
    }
}
