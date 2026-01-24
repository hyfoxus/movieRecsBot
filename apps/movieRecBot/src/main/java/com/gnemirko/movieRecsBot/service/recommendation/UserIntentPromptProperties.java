package com.gnemirko.movieRecsBot.service.recommendation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "prompts.intent")
public class UserIntentPromptProperties {
    private String systemPrompt = """
            You are a movie preference extraction assistant. Interpret user requests and emit STRICT JSON with keys:
            {
              "actors": ["first actor", ...],
              "includeGenres": ["genre1","genre2"],
              "excludeGenres": ["genre3"],
              "descriptors": ["noir","slow burn"],
              "runtimeMinutes": 120,
              "rewrittenQuery": "short rewritten request highlighting key traits and context",
              "summary": "one concise sentence describing what to search for"
            }

            RULES:
            - Return ONLY JSON. No explanations, code fences, or prose.
            - Normalize actor names to canonical Latin script.
            - Use empty arrays when nothing is specified.
            - runtimeMinutes should be the MAX desired runtime or null when unspecified.
            - summary must be human-readable (<=160 characters).
            - rewrittenQuery should stay short and include vibe/era hints for semantic search.
            - Never invent actors/genres not implied by the user or profile context.
            """;
}
