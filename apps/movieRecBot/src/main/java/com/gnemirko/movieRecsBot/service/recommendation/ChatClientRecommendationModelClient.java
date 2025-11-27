package com.gnemirko.movieRecsBot.service.recommendation;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ChatClientRecommendationModelClient implements RecommendationModelClient {

    private final ChatClient chatClient;

    @Override
    public String call(String systemPrompt, String userPrompt) {
        return chatClient
                .prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();
    }
}
