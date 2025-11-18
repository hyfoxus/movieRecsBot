package com.gnemirko.movieRecsBot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "telegram.bot.token=test-token",
        "telegram.bot.webhook-url=https://example.com",
        "telegram.bot.enable-webhook=false",
        "spring.ai.openai.api-key=test-key"
})
class MovieRecsBotApplicationTests {

    @Test
    void contextLoads() {
    }

}
