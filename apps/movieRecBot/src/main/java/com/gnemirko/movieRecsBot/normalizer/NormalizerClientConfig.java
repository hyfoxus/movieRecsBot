package com.gnemirko.movieRecsBot.normalizer;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(NormalizerProperties.class)
public class NormalizerClientConfig {

    @Bean
    public WebClient normalizerWebClient(NormalizerProperties properties,
                                         WebClient.Builder builder) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(1 * 1024 * 1024))
                .build();
        return builder
                .baseUrl(properties.getBaseUrl())
                .exchangeStrategies(strategies)
                .build();
    }
}
