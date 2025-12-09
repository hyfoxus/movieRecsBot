package com.gnemirko.movieRecsBot.service;

import java.util.regex.Pattern;

final class RecommendationMessageClassifier {

    private static final Pattern NUMBERED_BOLD_PATTERN =
            Pattern.compile("(?im)^\\s*(\\d+|[\\*-]|•)\\s*[\\.)-]?\\s*(<b>.+?</b>|<code>.+?</code>|\\*\\*.+?\\*\\*)");
    private static final Pattern BASIC_TAG_PATTERN =
            Pattern.compile("(?i)</?(b|i|u|s|code|br)>");

    private RecommendationMessageClassifier() {
    }

    static boolean looksLikeRecommendation(String raw) {
        if (raw == null) {
            return false;
        }
        String text = TelegramMessageFormatter.stripCodeFence(raw).trim();
        if (text.isEmpty()) {
            return false;
        }
        if (NUMBERED_BOLD_PATTERN.matcher(text).find()) {
            return true;
        }
        if ((countOccurrences(text, "<b>") + countOccurrences(text, "<code>")) >= 2 && text.contains("\n")) {
            return true;
        }
        return BASIC_TAG_PATTERN.matcher(text).find() && text.chars().filter(ch -> ch == '\n').count() >= 2;
    }

    private static int countOccurrences(String text, String needle) {
        if (needle == null || needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        int from = 0;
        while (from >= 0) {
            int idx = text.indexOf(needle, from);
            if (idx < 0) break;
            count++;
            from = idx + needle.length();
        }
        return count;
    }
}
