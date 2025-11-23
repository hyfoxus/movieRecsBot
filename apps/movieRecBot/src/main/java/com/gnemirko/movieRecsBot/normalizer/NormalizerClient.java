package com.gnemirko.movieRecsBot.normalizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class NormalizerClient {

    private final WebClient normalizerWebClient;
    private final NormalizerProperties properties;

    public NormalizationResponse normalize(String text, String targetLanguage) {
        try {
            NormalizationRequest body = new NormalizationRequest(text, targetLanguage);
            return normalizerWebClient.post()
                    .uri("/normalize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(NormalizationResponse.class)
                    .timeout(properties.getTimeout())
                    .onErrorResume(ex -> {
                        log.warn("Normalizer call failed: {}", ex.getMessage());
                        return Mono.empty();
                    })
                    .block(properties.getTimeout());
        } catch (Exception ex) {
            log.warn("Normalizer call aborted: {}", ex.getMessage());
            return null;
        }
    }
}
