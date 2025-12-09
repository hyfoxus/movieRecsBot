package com.gnemirko.movieRecsBot.normalizer;

import com.gnemirko.movieRecsBot.service.UserLanguage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TextNormalizer {

    private final NormalizerClient client;

    public NormalizedInput normalizeToEnglish(String text) {
        NormalizationResponse response = client.normalize(text, "en");
        if (response == null) {
            return new NormalizedInput(text, UserLanguage.englishFallback());
        }
        String normalized = nvl(stripPromptDelimiters(response.normalizedText()), text);
        UserLanguage language = UserLanguage.fromIsoCode(resolveDetectedIso(response.detectedLanguage()));
        return new NormalizedInput(normalized, language);
    }

    private static String nvl(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String resolveDetectedIso(String detected) {
        if (detected == null || detected.isBlank()) {
            return "en";
        }
        if ("unknown".equalsIgnoreCase(detected)) {
            return "en";
        }
        return detected;
    }

    private static String stripPromptDelimiters(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("<<<", "").replace(">>>", "").trim();
    }
}
