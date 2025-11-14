package com.gnemirko.movieRecsBot.service;

import com.github.pemistahl.lingua.api.IsoCode639_1;
import com.github.pemistahl.lingua.api.IsoCode639_3;
import com.github.pemistahl.lingua.api.Language;

import java.util.Locale;

/**
 * Value object that describes the language we expect the LLM to answer in.
 */
final class UserLanguage {

    private final String isoCode;
    private final String displayName;
    private final boolean detected;

    private UserLanguage(String isoCode, String displayName, boolean detected) {
        this.isoCode = isoCode == null || isoCode.isBlank()
                ? "en"
                : isoCode.toLowerCase(Locale.ROOT);
        this.displayName = displayName == null || displayName.isBlank()
                ? defaultDisplayName(this.isoCode)
                : displayName;
        this.detected = detected;
    }

    static UserLanguage fromLanguage(Language language) {
        if (language == null || language == Language.UNKNOWN) {
            return englishFallback();
        }
        String iso = resolveIso(language);
        return new UserLanguage(iso, toDisplayName(language), true);
    }

    static UserLanguage fromIsoCode(String isoCode) {
        if (isoCode == null || isoCode.isBlank()) {
            return englishFallback();
        }
        Locale locale = Locale.forLanguageTag(isoCode);
        String display = locale.getDisplayLanguage(Locale.ENGLISH);
        if (display == null || display.isBlank()) {
            display = isoCode.toUpperCase(Locale.ROOT);
        }
        return new UserLanguage(isoCode, display, true);
    }

    static UserLanguage englishFallback() {
        return new UserLanguage("en", "English", false);
    }

    String directive(String rawUserText) {
        if (!detected) {
            return "User language could not be detected reliably. Respond in English.";
        }
        return "The user's latest message is in " + displayName + " (" + isoCode + "). Respond strictly in that language.";
    }

    boolean requiresTranslation() {
        return !"en".equalsIgnoreCase(isoCode);
    }

    String isoCode() {
        return isoCode;
    }

    String displayName() {
        return displayName;
    }

    private static String resolveIso(Language language) {
        IsoCode639_1 iso6391 = language.getIsoCode639_1();
        if (iso6391 != null) {
            return iso6391.name();
        }
        IsoCode639_3 iso6393 = language.getIsoCode639_3();
        return iso6393 != null ? iso6393.name() : "und";
    }

    private static String toDisplayName(Language language) {
        String name = language.toString().toLowerCase(Locale.ROOT).replace('_', ' ');
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    private static String defaultDisplayName(String iso) {
        Locale locale = Locale.forLanguageTag(iso);
        String display = locale.getDisplayLanguage(Locale.ENGLISH);
        if (display == null || display.isBlank()) {
            return iso.toUpperCase(Locale.ROOT);
        }
        return display;
    }
}
