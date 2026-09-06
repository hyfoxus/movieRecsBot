package com.gnemirko.movieRecsBot.service.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.service.UserLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationResponseParserTest {

    private RecommendationResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new RecommendationResponseParser(new ObjectMapper());
    }

    @Test
    void parseFiltersFantasyRequestsAndBlockedTags() {
        UserProfile profile = new UserProfile();
        profile.getBlocked().add("genre:drama");

        String payload = """
                ```json
                {
                  "intro":"Ready for tonight?",
                  "language":"ru",
                  "movies":[
                    {"title":"Тайна вечера","reason":"Эмоциональное фэнтези","year":2020,"genres":["fantasy","drama"]},
                    {"title":"Случайный вечер","reason":"Повседневная драма","year":2022,"genres":["drama"]}
                  ],
                  "reminder":"Поделись мнением"
                }
                ```
                """;

        RecommendationResponseParser.ParsedResponse parsed = parser.parse(
                payload,
                profile,
                "Фэнтези фильм на вечер",
                UserLanguage.fromIsoCode("ru")
        );

        assertThat(parsed.movies()).hasSize(1);
        assertThat(parsed.movies().get(0).getTitle()).isEqualTo("Тайна вечера");
        assertThat(parsed.languageIso()).isEqualTo("ru");
        assertThat(parsed.reminder()).isEqualTo("Поделись мнением");
    }

    @Test
    void parseReturnsEmptyListWhenJsonMissing() {
        UserProfile profile = new UserProfile();

        RecommendationResponseParser.ParsedResponse parsed = parser.parse(
                "not a json",
                profile,
                "movie please",
                UserLanguage.fromIsoCode("en")
        );

        assertThat(parsed.movies()).isEmpty();
        assertThat(parsed.intro()).isEmpty();
    }

    @Test
    void parseHandlesNullProfile() {
        String payload = """
                {
                  "intro":"Ready?",
                  "language":"en",
                  "movies":[
                    {"title":"Sample","reason":"Because it fits","year":2020,"genres":["drama"]}
                  ],
                  "reminder":"Share feedback"
                }
                """;

        RecommendationResponseParser.ParsedResponse parsed = parser.parse(
                payload,
                null,
                "Movie please",
                UserLanguage.fromIsoCode("en")
        );

        assertThat(parsed.movies()).hasSize(1);
    }

    @Test
    void parseDropsMoviesMatchingExcludedTitleKeys() {
        String payload = """
                {
                  "intro":"Ready?",
                  "language":"en",
                  "movies":[
                    {"title":"Sample","reason":"Because it fits","year":2020,"genres":["drama"]},
                    {"title":"Other One","reason":"Also fits","year":2021,"genres":["drama"]}
                  ],
                  "reminder":"Share feedback"
                }
                """;

        RecommendationResponseParser.ParsedResponse parsed = parser.parse(
                payload,
                null,
                "Movie please",
                UserLanguage.fromIsoCode("en"),
                Set.of("sample|2020")
        );

        assertThat(parsed.movies()).hasSize(1);
        assertThat(parsed.movies().get(0).getTitle()).isEqualTo("Other One");
    }
}
