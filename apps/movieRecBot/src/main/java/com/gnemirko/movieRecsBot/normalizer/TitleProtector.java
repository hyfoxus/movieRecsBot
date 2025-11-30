package com.gnemirko.movieRecsBot.normalizer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Protects movie titles (rendered inside &lt;b&gt; tags) from being translated.
 */
final class TitleProtector {

    private static final Pattern TITLE_PATTERN =
            Pattern.compile("(?m)(\\d+\\.\\s*<b>)(.+?)(</b>)");

    private final String protectedText;
    private final Map<String, String> tokenToTitle;

    private TitleProtector(String protectedText, Map<String, String> tokenToTitle) {
        this.protectedText = protectedText;
        this.tokenToTitle = tokenToTitle;
    }

    static TitleProtector protect(String input) {
        if (input == null || input.isBlank()) {
            return new TitleProtector(input, Collections.emptyMap());
        }
        Matcher matcher = TITLE_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        Map<String, String> replacements = new LinkedHashMap<>();
        int idx = 0;
        while (matcher.find()) {
            String token = "__MOVIE_TITLE_" + idx++ + "__";
            replacements.put(token, matcher.group(2));
            matcher.appendReplacement(sb, matcher.group(1) + token + matcher.group(3));
        }
        matcher.appendTail(sb);
        if (replacements.isEmpty()) {
            return new TitleProtector(input, Collections.emptyMap());
        }
        return new TitleProtector(sb.toString(), replacements);
    }

    String protectedText() {
        return protectedText;
    }

    String restore(String translated) {
        if (translated == null || translated.isBlank() || tokenToTitle.isEmpty()) {
            return translated;
        }
        String restored = translated;
        for (Map.Entry<String, String> entry : tokenToTitle.entrySet()) {
            restored = restored.replace(entry.getKey(), entry.getValue());
        }
        return restored;
    }
}
