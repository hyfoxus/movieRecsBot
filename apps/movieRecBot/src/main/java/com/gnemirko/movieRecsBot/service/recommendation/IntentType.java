package com.gnemirko.movieRecsBot.service.recommendation;

public enum IntentType {
    RECOMMENDATION,
    INFORMATION;

    public static IntentType fromString(String value) {
        if (value == null || value.isBlank()) {
            return RECOMMENDATION;
        }
        try {
            return IntentType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return RECOMMENDATION;
        }
    }
}
