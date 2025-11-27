package com.gnemirko.movieRecsBot.normalizer;

public record NormalizationResponse(
        String originalText,
        String normalizedText,
        String detectedLanguage,
        boolean translationApplied,
        String notes
) {
}
