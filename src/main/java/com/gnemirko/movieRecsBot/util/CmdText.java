package com.gnemirko.movieRecsBot.util;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class CmdText {

    private static final String MDV2_SPECIAL = "_*[]()~`>#+-=|{}.!";

    private CmdText() {
    }

    public static List<String> parseArgs(String text) {
        if (text == null) {
            return List.of();
        }
        String payload = text;
        int sp = payload.indexOf(' ');
        if (sp >= 0) {
            payload = payload.substring(sp + 1);
        }
        return Arrays.stream(payload.split("[,;\\n]"))
                .map(String::trim)
                .filter(val -> !val.isEmpty())
                .collect(Collectors.toList());
    }

    public static String esc(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (char c : value.toCharArray()) {
            if (MDV2_SPECIAL.indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
