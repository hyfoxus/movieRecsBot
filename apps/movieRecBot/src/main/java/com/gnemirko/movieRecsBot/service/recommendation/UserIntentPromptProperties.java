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
              "summary": "one concise sentence describing what to search for",
              "intentType": "RECOMMENDATION or INFORMATION",
              "requestedTitle": "Movie title only when the user asks for info about a specific movie",
              "requestedYear": 0,
              "releaseYearFrom": 0,
              "reasoning": ["short bullet(s) explaining the classification"]
            }

            RULES:
            - Return ONLY JSON. No explanations, code fences, or prose.
            - Normalize actor names to canonical Latin script.
            - Use empty arrays when nothing is specified.
            - runtimeMinutes should be the MAX desired runtime or null when unspecified.
            - summary must be human-readable (<=160 characters).
            - rewrittenQuery should stay short and include vibe/era hints for semantic search.
            - Never invent actors/genres not implied by the user or profile context.
            - For INFORMATION intents, actors/includeGenres/etc. can be empty.
            - releaseYearFrom must be an integer year (or null) representing the minimum release year requested by the user. When the user says "recent", "fresh", "latest", "new", "самый свежий", etc., set releaseYearFrom to the current year or previous year (>= currentYear - 1). Otherwise null.
            - reasoning should briefly justify why you chose the intent type and filters.
            """;

    private String classificationPrompt = """
            You are an intent classifier for a movie assistant. Determine if the user is:
            - Asking for factual information about a known movie (cast, plot, runtime, rating, trivia, quotes, "who played", "actors in", "tell me about").
            - Asking for recommendations / discovery help (looking for movies to watch, describing preferences, asking for similar titles).

            Emit STRICT JSON with keys:
            {
              "intentType": "RECOMMENDATION or INFORMATION",
              "requestedTitle": "Exact movie title when INFORMATION, otherwise empty string",
              "requestedYear": 0,
              "releaseYearFrom": 0,
              "summary": "Short sentence describing what the user wants",
              "reasoning": ["bullet list describing how you decided"]
            }

            RULES:
            - Return ONLY JSON. No prose, no code fences.
            - INFORMATION intentType MUST be used whenever the user references a specific movie to learn about (cast, plot, rating, trivia, etc.).
            - RECOMMENDATION intentType must be used whenever the user is looking for a list/set of movies to watch (e.g., "назови три свежих фильма...", "top movies with <actor>", "recommend", "подскажи фильм"), even if they mention an actor or era. Lists/searches always imply discovery, not information.
            - requestedTitle must capture the precise movie title mentioned when INFORMATION (omit year and quotes). If multiple titles appear, choose the one the user wants details about.
            - releaseYearFrom should be the minimum release year the user implies (set to current year or previous year when words like "recent", "fresh", "latest", "newest", "свежий", "новый", "последний" appear). Otherwise leave null.
            - requestedYear should be the release year mentioned by the user (integer). Use null when the user did not specify a year.
            - summary should stay under 160 characters.
            - reasoning should capture the clues (e.g., "user asked who played in <title>" or "user wants suggestions with actor preferences").

            Example:
            User: "Кто играл в Однажды в Голливуде?"
            {
              "intentType": "INFORMATION",
              "requestedTitle": "Once Upon a Time in Hollywood",
              "requestedYear": 2019,
              "releaseYearFrom": null,
              "summary": "User wants the cast list for Once Upon a Time in Hollywood.",
              "reasoning": ["phrase 'кто играл' means 'who played', a factual request"]
            }

            User: "Нужна комедия с Аль Пачино"
            {
              "intentType": "RECOMMENDATION",
              "requestedTitle": "",
              "requestedYear": null,
              "releaseYearFrom": null,
              "summary": "User wants a comedy recommendation starring Al Pacino.",
              "reasoning": ["user requested a movie suggestion with actor preference"]
            }

            User: "Назови три самых свежих фильма, где играет Кевин Костнер"
            {
              "intentType": "RECOMMENDATION",
              "requestedTitle": "",
              "requestedYear": null,
              "releaseYearFrom": 2025,
              "summary": "User wants the latest movies starring Kevin Costner to watch.",
              "reasoning": ["user asked for three movies to watch, not facts about one title"]
            }
            """;
}
