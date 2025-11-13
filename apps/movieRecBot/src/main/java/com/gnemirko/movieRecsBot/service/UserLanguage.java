package com.gnemirko.movieRecsBot.service;

import com.github.pemistahl.lingua.api.IsoCode639_1;
import com.github.pemistahl.lingua.api.IsoCode639_3;
import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;

import java.util.Locale;

/**
 * Language metadata detected from the user's latest message.
 * Defaults to English when detection fails, per product requirement.
 */
final class UserLanguage {

    private static final LanguageDetector DETECTOR = LanguageDetectorBuilder.fromAllLanguages().build();

    private final Language detected;
    private final String isoCode;
    private final String displayName;

    private UserLanguage(Language language) {
        if (language == null || language == Language.UNKNOWN) {
            this.detected = null;
            this.isoCode = "en";
            this.displayName = "English";
        } else {
            this.detected = language;
            String iso;
            IsoCode639_1 iso6391 = language.getIsoCode639_1();
            if (iso6391 != null) {
                iso = iso6391.name();
            } else {
                IsoCode639_3 iso6393 = language.getIsoCode639_3();
                iso = iso6393 != null ? iso6393.name() : "";
            }
            this.isoCode = iso.isBlank() ? "und" : iso;
            this.displayName = toDisplayName(language);
        }
    }

    static UserLanguage detect(String text) {
        if (text == null || text.isBlank()) {
            return new UserLanguage(null);
        }
        try {
            return new UserLanguage(DETECTOR.detectLanguageOf(text));
        } catch (Exception e) {
            return new UserLanguage(null);
        }
    }

    String directive(String rawUserText) {
        if (detected == null) {
            return "User language could not be detected reliably. Respond in English.";
        }
        return "The user's latest message is in " + displayName + " (" + isoCode + "). Respond strictly in that language.";
    }

    boolean requiresTranslation() {
        return detected != null && detected != Language.ENGLISH;
    }

    String isoCode() {
        return isoCode;
    }

    String displayName() {
        return displayName;
    }

    private static String toDisplayName(Language language) {
        String name = language.toString().toLowerCase(Locale.ROOT).replace('_', ' ');
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }
}
