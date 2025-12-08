package com.gnemirko.movieRecsBot.service.recommendation;

import com.gnemirko.movieRecsBot.service.UserLanguage;
import org.springframework.stereotype.Component;

@Component
public class RecommendationPromptBuilder {

    private static final String BASE_SYSTEM = """
            You are MovieMate, a movie recommendation assistant.

            RULES:
            1) Ask at most TWO clarifying questions per conversation. If the user explicitly says “give recommendations” or “no questions” — go straight to recommendations.
            2) When the info is scarce, make reasonable assumptions (modern, not kids content, any language) and still respond with recommendations.
            3) Do not repeat questions and never ask another question after the user requests recommendations.
            4) Always honor the user’s genre preferences and block lists.
            5) Keep it short: 3–5 movies, each with one concise reason it fits.
            6) Stay on the movie topic (genres, vibe, actors, recent watches) to understand the request better.
            7) Be honest about metadata: never make up cast, release year, or genre details.
            8) Treat the CATALOG FACTS block as the source of truth for canonical title/year/genre; copy those values directly and never invent a year.
            9) When no available movie satisfies the stated constraints (year, creator, genre, etc.), explicitly say there are no matches before asking follow-up questions or proposing alternative ideas.

            FORMAT FOR TELEGRAM (HTML):
            - Start with a one-line intro.
            - Then provide a numbered list.
            - Render each title + year in monospace so users can copy it easily: <code>Title (Year)</code>.
            - No links or extra symbols beyond the numbered list.
            """;

    private static final String ASK_OR_RECOMMEND_PROMPT = """
            If you already have enough information to recommend, reply with exactly "__RECOMMEND__".
            If the request cannot be satisfied at all, first respond with a clear sentence like "I can’t find any movies that satisfy those requirements." before anything else.
            Otherwise ask exactly one new clarifying question with no preface and no numbering.
            """;

    private static final String JSON_RESPONSE_PROMPT_TEMPLATE = """
            Return ONLY JSON with this structure:
            {
              "language": "%s",
              "intro": "short intro",
              "movies": [
                {"title":"...", "year":1999, "reason":"...", "genres":["Fantasy","..."]}
              ],
              "reminder": "localized version of '%s'"
            }
            Return 3–5 movies unless the constraints make that impossible, in which case "movies" MUST be an empty list.
            Obey all user genre preferences and block lists. Ensure the "language" value
            exactly matches the target ISO code. If a movie exists in the CATALOG FACTS block, copy its title and
            year exactly from there. When the catalog lacks a year, set "year": null instead of guessing. NEVER translate
            movie titles or years — always reuse the exact values from the catalog or IMDb even if responding in another language.
            When "movies" is empty, use "intro" (and optionally "reminder") to clearly state that no movies satisfy the request before suggesting any adjustments or questions.
            """;

    private static final String REMINDER_MESSAGE = "When you watch something, send /watched with your thoughts so I can improve.";

    public String buildQuestionSystemPrompt(UserLanguage language, String userText) {
        return baseSystem(language, userText) + "\n\n" + ASK_OR_RECOMMEND_PROMPT;
    }

    public String buildRecommendationSystemPrompt(UserLanguage language, String userText) {
        return baseSystem(language, userText) + "\n\n" + jsonResponsePrompt(language);
    }

    public String buildUserPrompt(PromptContext context, String userText) {
        StringBuilder builder = new StringBuilder();
        if (!context.history().isBlank()) {
            builder.append("History (truncated):\n")
                    .append(context.history())
                    .append("\n\n");
        }
        if (!context.profileSummary().isBlank()) {
            builder.append("User profile:\n")
                    .append(context.profileSummary())
                    .append("\n\n");
        }
        if (context.movieContext() != null && !context.movieContext().isBlank()) {
            builder.append(context.movieContext().trim()).append("\n\n");
        }
        builder.append("User: ").append(userText);
        return builder.toString();
    }

    private String baseSystem(UserLanguage language, String userText) {
        String strictness = language.directive(userText)
                + " Preserve official movie titles and years EXACTLY as provided in the CATALOG FACTS section or IMDb; never translate or paraphrase titles.";
        return BASE_SYSTEM + "\n\nLANGUAGE RULE:\n" + strictness;
    }

    private String jsonResponsePrompt(UserLanguage language) {
        return JSON_RESPONSE_PROMPT_TEMPLATE.formatted(language.isoCode(), REMINDER_MESSAGE);
    }
}
