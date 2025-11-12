package com.gnemirko.movieRecsBot.service;

public final class TelegramMessageFormatter {

    private TelegramMessageFormatter() {
    }

    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String unfenced = stripCodeFence(raw).trim();
        if (unfenced.isEmpty()) {
            return "";
        }
        String escaped = escapeHtml(unfenced);
        return scrubMarkdownArtifacts(escaped).trim();
    }

    public static String stripCodeFence(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        String withoutFence = trimmed.replaceFirst("^```[a-zA-Z0-9]*\\s*", "");
        int fenceEnd = withoutFence.lastIndexOf("```");
        if (fenceEnd >= 0) {
            withoutFence = withoutFence.substring(0, fenceEnd);
        }
        return withoutFence.trim();
    }

    public static String scrubMarkdownArtifacts(String html) {
        if (html == null || html.isEmpty()) return "";
        String out = html;
        out = out.replaceAll("(?s)\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        out = out.replaceAll("(?s)__([^_]+?)__", "<u>$1</u>");
        out = out.replaceAll("(?s)~~([^~]+?)~~", "<s>$1</s>");
        out = out.replaceAll("(?s)`([^`]+?)`", "<code>$1</code>");
        out = out.replace("*", "");
        return out;
    }

    public static String htmlToPlain(String html) {
        if (html == null || html.isEmpty()) return "";
        String plain = html
                .replace("<br/>", "\n")
                .replace("<br />", "\n")
                .replace("<br>", "\n");
        plain = plain.replaceAll("<[^>]+>", "");
        return plain
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    public static String escapeHtml(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                case '\n' -> out.append("\n");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
