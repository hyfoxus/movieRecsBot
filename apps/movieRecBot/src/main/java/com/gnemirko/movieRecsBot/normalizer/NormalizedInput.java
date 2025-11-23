package com.gnemirko.movieRecsBot.normalizer;

import com.gnemirko.movieRecsBot.service.UserLanguage;

public record NormalizedInput(String normalizedText, UserLanguage language) {
}
