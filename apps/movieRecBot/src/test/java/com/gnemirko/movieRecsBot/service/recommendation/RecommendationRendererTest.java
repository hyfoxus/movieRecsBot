package com.gnemirko.movieRecsBot.service.recommendation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnemirko.movieRecsBot.entity.UserProfile;
import com.gnemirko.movieRecsBot.service.UserLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationRendererTest {

    private RecommendationRenderer renderer;
    private RecommendationResponseParser parser;

    @BeforeEach
    void setUp() {
        renderer = new RecommendationRenderer();
        parser = new RecommendationResponseParser(new ObjectMapper());
    }

    @Test
    void renderProducesTelegramFriendlyHtml() {
        String payload = """
                {
                  "intro":"**Лучшие подборки**",
                  "language":"ru",
                  "movies":[
                    {"title":"Вечерняя история (2021)","reason":"*Теплая* драма","year":2021},
                    {"title":"Night Ride","reason":"Смешивает _жанры_","year":2020},
                    {"title":"Third","reason":"Test"},
                    {"title":"Fourth","reason":"Test"},
                    {"title":"Fifth","reason":"Test"},
                    {"title":"Sixth","reason":"Should be trimmed"}
                  ],
                  "reminder":"Поделись мнением"
                }
                """;

        RecommendationResponseParser.ParsedResponse parsed = parser.parse(
                payload,
                new UserProfile(),
                "movie",
                UserLanguage.fromIsoCode("ru")
        );

        String rendered = renderer.render(parsed);

        assertThat(rendered).startsWith("<b>Лучшие подборки</b>");
        assertThat(rendered).contains("1. <code>Вечерняя история (2021)</code> — Теплая драма");
        assertThat(rendered).contains("2. <code>Night Ride (2020)</code> — Смешивает жанры");
        assertThat(rendered).doesNotContain("6.");
    }
}
