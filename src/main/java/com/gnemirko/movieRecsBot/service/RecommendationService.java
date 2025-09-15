package com.gnemirko.movieRecsBot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final ChatClient chatClient;
    private final UserContextService userContextService;

    private static final String SYSTEM = """
        You are MovieMate, a concise film recommender.
        Ask 1–2 clarifying questions if needed.
        Return 3–5 titles with year and a one-sentence hook for each.
        Prefer variety and briefly explain why each movie fits.
        """;

    public String reply(long chatId, String userText) {
        var history = userContextService.historyAsOneString(chatId, 30, 300);

        var builder = chatClient
                .prompt()
                .system(SYSTEM);

        if (!history.isBlank()) {
            builder = builder.user("Conversation so far:\n" + history);
        }

        String content = builder
                .user(userText)
                .options(OpenAiChatOptions.builder()
                        .model("gpt-4o-mini")
                        .temperature(0.4)
                        .build())
                .call()
                .content();

        userContextService.append(chatId, "User: " + userText);
        userContextService.append(chatId, "Bot: " + content);
        return content;
    }
}