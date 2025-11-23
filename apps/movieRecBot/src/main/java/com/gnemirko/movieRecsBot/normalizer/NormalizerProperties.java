package com.gnemirko.movieRecsBot.normalizer;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "app.normalizer")
public class NormalizerProperties {

    private String baseUrl = "http://normalizer:8083";
    private Duration timeout = Duration.ofSeconds(5);
}
