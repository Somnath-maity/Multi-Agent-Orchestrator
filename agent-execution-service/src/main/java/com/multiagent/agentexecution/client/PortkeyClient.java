package com.multiagent.agentexecution.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Client for the Portkey API (OpenAI-compatible chat completions endpoint).
 * Uses Spring WebClient for non-blocking HTTP, with Resilience4j retry and circuit breaker.
 */
@Slf4j
@Component
public class PortkeyClient {

    private final WebClient webClient;
    private final String apiKey;

    public PortkeyClient(
            WebClient.Builder webClientBuilder,
            @Value("${portkey.api-key}") String apiKey,
            @Value("${portkey.base-url}") String baseUrl,
            @Value("${portkey.provider}") String provider
    ) {
        this.apiKey = apiKey;
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("x-portkey-api-key", apiKey)
                .defaultHeader("x-portkey-provider", provider)
                .build();
    }

    /**
     * Sends a chat completion request to the Portkey API (reactive).
     *
     * @param model       the model identifier (e.g., "gpt-4o-mini")
     * @param messages    list of message maps with "role" and "content" keys
     * @param temperature sampling temperature (0.0 - 2.0)
     * @return Mono emitting the response content string
     */
    @Retry(name = "portkey-call")
    @CircuitBreaker(name = "portkey-call")
    @SuppressWarnings("unchecked")
    public Mono<String> chatCompletion(String model, List<Map<String, String>> messages, double temperature) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages,
                "temperature", temperature
        );

        log.debug("Sending chat completion request to Portkey: model={}, messageCount={}", model, messages.size());

        return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .exchangeToMono(response -> {
                    if (response.statusCode().isError()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("(empty body)")
                                .flatMap(body -> {
                                    log.error("Portkey API error {}: {}", response.statusCode(), body);
                                    return Mono.error(new RuntimeException(
                                            "Portkey " + response.statusCode() + ": " + body));
                                });
                    }
                    return response.bodyToMono(Map.class);
                })
                .map(response -> {
                    var choices = (List<Map<String, Object>>) response.get("choices");
                    if (choices == null || choices.isEmpty()) {
                        throw new RuntimeException("Empty choices in Portkey response");
                    }
                    var message = (Map<String, Object>) choices.getFirst().get("message");
                    return (String) message.get("content");
                })
                .doOnSuccess(content -> log.debug("Portkey response received: {} chars", content.length()))
                .doOnError(err -> log.error("Portkey API call failed: {}", err.getMessage()));
    }

    /**
     * Blocking wrapper around {@link #chatCompletion} for synchronous callers.
     */
    @Retry(name = "portkey-call")
    @CircuitBreaker(name = "portkey-call")
    public String chatCompletionSync(String model, List<Map<String, String>> messages, double temperature) {
        return chatCompletion(model, messages, temperature).block();
    }
}
