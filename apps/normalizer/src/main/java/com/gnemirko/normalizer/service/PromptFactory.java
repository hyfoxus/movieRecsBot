package com.gnemirko.normalizer.service;

public final class PromptFactory {

    private PromptFactory() {
    }

    public static String languageDetectionPrompt(String text) {
        String sanitized = text == null ? "" : text;
        return """
                You are a language identification engine. Respond with valid JSON only in this exact shape:
                {"language":"<iso-639-1>","confidence":0.0,"label":"<language name>"}
                Use "unknown" when unsure. Analyze the following text:
                <<<%s>>>
                """.formatted(sanitized);
    }

    public static String translationPrompt(String text, String sourceLanguage, String targetLanguage) {
        String sanitized = text == null ? "" : text;
        return """
                You are a precise translator.
                Input language: %s
                Output language: %s
                Rules:
                - Preserve movie titles, names, and entities.
                - Keep HTML/markup tags exactly as provided.
                - Do not add commentary.
                - Return only the translated text.
                Text:
                <<<%s>>>
                """.formatted(sourceLanguage, targetLanguage, sanitized);
    }
}
