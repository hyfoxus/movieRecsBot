package com.gnemirko.movieRecsBot.service.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.service.UserLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationResponseParserTest {

    private RecommendationResponseParser parser;

    @BeforeEach
    void setUp() {
        parser = new RecommendationResponseParser(new ObjectMapper());
    }

    @Test
    void parsesJsonPayloadInsideCodeFence() {
        String payload = """
                ```json
                {
                  "language": "ru",
                  "intro": "Привет",
                  "movies": [
                    {"title":"Inception","year":2010,"reason":"Mind-bending","genres":["Sci-Fi"]},
                    {"title":"Test","year":2020,"reason":"Should be filtered","genres":["Drama"]}
                  ]
                }
                ```
                """;
        UserProfile profile = UserProfile.builder()
                .likedGenres(new LinkedHashSet<>(List.of("sci-fi")))
                .blocked(new LinkedHashSet<>(List.of("test")))
                .build();

        RecommendationResponseParser.ParsedResponse response = parser.parse(
                payload,
                profile,
                "что-нибудь научное",
                UserLanguage.englishFallback()
        );

        assertThat(response.movies()).hasSize(1);
        RecommendationMovie movie = response.movies().get(0);
        assertThat(movie.getTitle()).isEqualTo("Inception");
        assertThat(movie.getYear()).isEqualTo(2010);
    }
}
