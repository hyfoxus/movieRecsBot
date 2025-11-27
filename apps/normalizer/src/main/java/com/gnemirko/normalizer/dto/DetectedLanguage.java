package com.gnemirko.normalizer.dto;

import lombok.Builder;

@Builder
public record DetectedLanguage(String code, double confidence, String label) {

    public static DetectedLanguage unknown() {
        return DetectedLanguage.builder()
                .code("unknown")
                .confidence(0.0)
                .label("")
                .build();
    }
}
