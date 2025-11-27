package com.gnemirko.normalizer.dto;

import lombok.Builder;

@Builder
public record NormalizationResponse(
        String originalText,
        String normalizedText,
        String detectedLanguage,
        boolean translationApplied,
        String notes
) {
}
