package com.gnemirko.movieRecsBot.normalizer;

import com.gnemirko.movieRecsBot.service.UserLanguage;

public record NormalizedInput(String originalText,
                              String normalizedText,
                              UserLanguage language) {

    public NormalizedInput {
        originalText = originalText == null ? "" : originalText;
        normalizedText = normalizedText == null ? "" : normalizedText;
        language = language == null ? UserLanguage.englishFallback() : language;
    }
}
