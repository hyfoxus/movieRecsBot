package com.gnemirko.normalizer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "normalizer.ollama")
public class NormalizerOllamaProperties {

    private String baseUrl = "http://localhost:11434";
    private String detectionModel = "llama3.1:8b";
    private String translationModel = "llama3.1:8b";
    private Duration timeout = Duration.ofSeconds(15);
    private double temperature = 0.1;
}
