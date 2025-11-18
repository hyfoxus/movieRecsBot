package com.gnemirko.movieRecsBot.service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ActorMentionExtractor {

    private static final Pattern MULTI_WORD_NAME = Pattern.compile(
            "(?:(?<=^)|(?<=\\s|[,.:;!?]))([\\p{Lu}][\\p{L}'-]+(?:\\s+[\\p{Lu}][\\p{L}'-]+)+)"
    );

    private ActorMentionExtractor() {
    }

    static Set<String> extract(String text) {
        Set<String> names = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return names;
        }
        Matcher matcher = MULTI_WORD_NAME.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate == null) continue;
            String normalized = candidate.trim().replaceAll("\\s+", " ");
            if (normalized.length() < 4) continue;
            names.add(normalized);
        }
        return names;
    }
}
