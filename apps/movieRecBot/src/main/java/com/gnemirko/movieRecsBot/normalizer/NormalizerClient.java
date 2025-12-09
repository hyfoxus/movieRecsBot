package com.gnemirko.movieRecsBot.normalizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@Slf4j
public class NormalizerClient {

    private final WebClient normalizerWebClient;
    private final NormalizerProperties properties;

    public NormalizationResponse normalize(String text, String targetLanguage) {
        try {
            NormalizationRequest body = new NormalizationRequest(text, targetLanguage);
            Duration timeout = properties.getTimeout();
            NormalizationResponse response = normalizerWebClient.post()
                    .uri("/normalize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(NormalizationResponse.class)
                    .timeout(timeout)
                    .onErrorResume(ex -> {
                        if (ex instanceof TimeoutException) {
                            log.warn("Normalizer call timed out after {} for target '{}'", timeout, targetLanguage);
                        } else {
                            log.warn("Normalizer call failed: {}", ex.getMessage());
                        }
                        return Mono.empty();
                    })
                    .block(timeout);
            if (response != null) {
                log.info("Normalizer translation succeeded for target '{}', detected='{}'", targetLanguage, response.detectedLanguage());
            }
            return response;
        } catch (Exception ex) {
            log.warn("Normalizer call aborted: {}", ex.getMessage());
            return null;
        }
    }
}
