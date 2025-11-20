package com.gnemirko.movieRecsBot.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TitleFormatter {

    private static final Pattern[] TRAILING_YEAR_PATTERNS = new Pattern[]{
            Pattern.compile("\\s*[\\(\\[]?(?:18|19|20|21)\\d{2}[\\)\\]]?\\s*$"),
            Pattern.compile("\\s*[–—-]\\s*(?:18|19|20|21)\\d{2}\\s*$"),
            Pattern.compile("\\s*,\\s*(?:18|19|20|21)\\d{2}\\s*$")
    };

    private TitleFormatter() {
    }

    public static String formatWithVerifiedYear(String title, Integer verifiedYear) {
        String cleaned = stripTrailingYear(title);
        if (verifiedYear == null) {
            return cleaned;
        }
        return cleaned.isEmpty()
                ? "(" + verifiedYear + ")"
                : cleaned + " (" + verifiedYear + ")";
    }

    public static String stripTrailingYear(String title) {
        if (title == null) {
            return "";
        }
        String trimmed = title.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        for (Pattern pattern : TRAILING_YEAR_PATTERNS) {
            Matcher matcher = pattern.matcher(trimmed);
            if (matcher.find()) {
                return trimmed.substring(0, matcher.start()).trim();
            }
        }
        return trimmed;
    }
}
