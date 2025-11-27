package com.gnemirko.movieRecsBot.service.recommendation;

public interface RecommendationModelClient {

    String call(String systemPrompt, String userPrompt);
}
