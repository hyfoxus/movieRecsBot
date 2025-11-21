package com.gnemirko.normalizer.ollama;

import com.gnemirko.normalizer.config.NormalizerOllamaProperties;
import com.gnemirko.normalizer.dto.GenerateRequest;
import com.gnemirko.normalizer.dto.GenerateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class OllamaClient implements CompletionClient {

    private final WebClient ollamaWebClient;
    private final NormalizerOllamaProperties properties;

    @Override
    public String complete(String model, String prompt) {
        GenerateRequest request = new GenerateRequest(model, prompt, false,
                new GenerateRequest.Options(properties.getTemperature()));
        return ollamaWebClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GenerateResponse.class)
                .timeout(properties.getTimeout())
                .onErrorResume(ex -> {
                    log.warn("Ollama generate call failed: {}", ex.getMessage());
                    return Mono.empty();
                })
                .map(GenerateResponse::response)
                .map(String::trim)
                .block(properties.getTimeout());
    }
}
