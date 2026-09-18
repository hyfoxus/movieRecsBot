package com.gnemirko.normalizer.ollama;

import com.gnemirko.normalizer.config.NormalizerOllamaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaClientTest {

    private NormalizerOllamaProperties properties() {
        NormalizerOllamaProperties props = new NormalizerOllamaProperties();
        props.setTimeout(Duration.ofSeconds(2));
        props.setTemperature(0.1);
        return props;
    }

    private OllamaClient clientWithExchange(java.util.function.Function<ClientRequest, Mono<ClientResponse>> exchange) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://ollama.local")
                .exchangeFunction(exchange::apply)
                .build();
        return new OllamaClient(webClient, properties());
    }

    @Test
    void returnsTrimmedResponseTextOnSuccess() {
        OllamaClient client = clientWithExchange(request ->
                Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("{\"response\":\"  Hello Brad Pitt  \",\"done\":true}")
                        .build()));

        String result = client.complete("llama3.1:8b", "translate: hi");

        assertThat(result).isEqualTo("Hello Brad Pitt");
    }

    @Test
    void postsToGenerateEndpointWithJsonContentType() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        OllamaClient client = clientWithExchange(request -> {
            captured.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body("{\"response\":\"ok\",\"done\":true}")
                    .build());
        });

        client.complete("llama3.1:8b", "hello");

        ClientRequest request = captured.get();
        assertThat(request.method()).isEqualTo(HttpMethod.POST);
        assertThat(request.url().toString()).isEqualTo("http://ollama.local/api/generate");
        assertThat(request.headers().getContentType().toString()).contains("application/json");
    }

    @Test
    void returnsNullWhenDownstreamCallErrors() {
        OllamaClient client = clientWithExchange(request -> Mono.error(new RuntimeException("connection refused")));

        String result = client.complete("llama3.1:8b", "hello");

        assertThat(result).isNull();
    }

    @Test
    void returnsNullWhenResponseStatusIsError() {
        OllamaClient client = clientWithExchange(request ->
                Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                        .header("Content-Type", "application/json")
                        .body("{\"error\":\"boom\"}")
                        .build()));

        String result = client.complete("llama3.1:8b", "hello");

        assertThat(result).isNull();
    }

    @Test
    void returnsNullWhenCallExceedsConfiguredTimeout() {
        NormalizerOllamaProperties props = new NormalizerOllamaProperties();
        props.setTimeout(Duration.ofMillis(50));
        WebClient webClient = WebClient.builder()
                .baseUrl("http://ollama.local")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                                .header("Content-Type", "application/json")
                                .body("{\"response\":\"late\",\"done\":true}")
                                .build())
                        .delayElement(Duration.ofMillis(300)))
                .build();
        OllamaClient client = new OllamaClient(webClient, props);

        String result = client.complete("llama3.1:8b", "hello");

        assertThat(result).isNull();
    }
}
