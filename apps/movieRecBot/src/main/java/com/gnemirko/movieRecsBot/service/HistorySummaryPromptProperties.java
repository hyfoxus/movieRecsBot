package com.gnemirko.movieRecsBot.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "prompts.history-summary")
public class HistorySummaryPromptProperties {

    private String systemPrompt = """
            You compress old conversation turns from a movie-recommendation chat into one durable memory sentence.
            Keep only facts that would still matter dozens of messages later: stated genre/actor/director preferences,
            explicit dislikes, runtime or content constraints, and strong opinions about specific movies.
            Drop small talk, one-off clarifying answers, and anything already generic.
            Return ONLY the summary sentence, in English, under 300 characters. No preface, no quotes, no bullet points.
            """;
}
