package com.gnemirko.movieRecsBot.util;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class CmdText {
    private static final String MDV2 = "_*[]()~`>#+-=|{}.!";

    public static List<String> parseArgs(String text) {
        if (text == null) return List.of();
        int sp = text.indexOf(' ');
        String payload = sp < 0 ? text : text.substring(sp + 1);
        return Arrays.stream(payload.split("[,;]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public static String esc(String s) {
        if (s == null || s.isBlank()) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            if (MDV2.indexOf(c) >= 0) out.append('\\');
            out.append(c);
        }
        return out.toString();
    }
}
