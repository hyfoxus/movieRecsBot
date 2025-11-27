package com.gnemirko.normalizer.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(NormalizerOllamaProperties.class)
public class NormalizerConfig {

    @Bean
    public WebClient ollamaWebClient(NormalizerOllamaProperties properties,
                                     WebClient.Builder builder) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
        return builder
                .baseUrl(properties.getBaseUrl())
                .exchangeStrategies(strategies)
                .build();
    }
}
