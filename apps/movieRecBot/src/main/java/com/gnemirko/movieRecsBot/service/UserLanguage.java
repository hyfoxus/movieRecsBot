package com.gnemirko.movieRecsBot.service;

import java.util.Locale;

/**
 * Value object that describes the language we expect the LLM to answer in.
 */
public final class UserLanguage {

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

    static UserLanguage fromIsoCode(String isoCode) {
        return fromIsoCode(isoCode, null);
    }

    static UserLanguage fromIsoCode(String isoCode, String displayName) {
        if (isoCode == null || isoCode.isBlank()) {
            return englishFallback();
        }
        String name = (displayName == null || displayName.isBlank())
                ? defaultDisplayName(isoCode)
                : displayName;
        return new UserLanguage(isoCode, name, true);
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

    public String isoCode() {
        return isoCode;
    }

    public String displayName() {
        return displayName;
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
