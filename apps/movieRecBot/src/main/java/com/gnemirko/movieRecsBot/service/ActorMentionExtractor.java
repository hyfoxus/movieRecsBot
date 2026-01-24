package com.gnemirko.movieRecsBot.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ActorMentionExtractor {

    private static final Pattern TITLE_CASE_NAME = Pattern.compile(
            "(?:(?<=^)|(?<=\\s|[,.:;!?]))([\\p{Lu}][\\p{L}'-]+(?:\\s+[\\p{Lu}][\\p{L}'-]+)+)"
    );

    private static final Pattern HINTED_LOWERCASE_NAME = Pattern.compile(
            "(?iu)(?:\\bwith\\b|\\bactors?\\b|\\bактерами\\b|\\bактёрами\\b|\\bактрисами\\b|(?<!\\p{L})с(?=\\s))\\s+([\\p{L}][\\p{L}'-]+(?:\\s+[\\p{L}][\\p{L}'-]+){1,7})"
    );

    private static final Set<String> TRAILING_STOPWORDS = Set.of(
            "please", "пожалуйста", "или", "and", "и", "pls", "пж", "плиз"
    );


    private ActorMentionExtractor() {
    }

    private static final Pattern CONNECTOR_SPLIT = Pattern.compile(
            "\\s*(?:,|/|\\band\\b|\\bor\\b|\\bamp\\b|\\bwith\\b|&|\\+|\\bи\\b|\\bили\\b)\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    public static Set<String> extract(String text) {
        Set<String> names = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return names;
        }
        collectMatches(TITLE_CASE_NAME.matcher(text), names);
        collectMatches(HINTED_LOWERCASE_NAME.matcher(text), names);
        return names;
    }

    private static void collectMatches(Matcher matcher, Set<String> names) {
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate == null) {
                continue;
            }
            List<String> fragments = splitCompositeCandidate(candidate);
            for (String fragment : fragments) {
                String normalized = fragment.trim().replaceAll("\\s+", " ");
                if (normalized.length() < 4) {
                    continue;
                }
                String cleaned = stripTrailingStopwords(normalized);
                if (wordCount(cleaned) < 2) {
                    continue;
                }
                names.add(toTitleCase(cleaned));
            }
        }
    }

    private static int wordCount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return (int) Arrays.stream(value.trim().split("\\s+"))
                .filter(part -> !part.isBlank())
                .count();
    }

    private static String stripTrailingStopwords(String value) {
        List<String> tokens = new ArrayList<>(Arrays.asList(value.split("\\s+")));
        while (tokens.size() > 1) {
            String last = tokens.get(tokens.size() - 1);
            String sanitized = sanitizeToken(last);
            if (sanitized.isEmpty()) {
                tokens.remove(tokens.size() - 1);
                continue;
            }
            if (!TRAILING_STOPWORDS.contains(sanitized.toLowerCase(Locale.ROOT))) {
                break;
            }
            tokens.remove(tokens.size() - 1);
        }
        return String.join(" ", tokens);
    }

    private static String sanitizeToken(String token) {
        return token.replaceAll("^[^\\p{L}]+|[^\\p{L}]+$", "");
    }

    private static List<String> splitCompositeCandidate(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        String[] parts = CONNECTOR_SPLIT.split(trimmed);
        List<String> fragments = new ArrayList<>();
        for (String part : parts) {
            String clean = part.trim();
            if (!clean.isEmpty()) {
                fragments.add(clean);
            }
        }
        if (fragments.isEmpty()) {
            fragments.add(trimmed);
        }
        return fragments;
    }

    private static String toTitleCase(String value) {
        String[] parts = value.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(capitalizeWord(part));
        }
        return builder.toString().trim();
    }

    private static String capitalizeWord(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return lower;
        }
        int firstCodePoint = lower.codePointAt(0);
        int charCount = Character.charCount(firstCodePoint);
        String first = new String(Character.toChars(Character.toUpperCase(firstCodePoint)));
        String rest = lower.substring(charCount);
        return first + rest;
    }
}
