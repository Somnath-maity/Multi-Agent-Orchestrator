package com.multiagent.agentexecution.controller;

import com.multiagent.agentexecution.client.PortkeyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final PortkeyClient portkeyClient;

    @Value("${portkey.default-model}")
    private String defaultModel;

    @GetMapping("/portkey")
    public ResponseEntity<Map<String, Object>> testPortkey() {
        long start = System.currentTimeMillis();
        try {
            String response = portkeyClient.chatCompletionSync(
                    defaultModel,
                    List.of(Map.of("role", "user", "content", "Reply with exactly: OK")),
                    0.0
            );
            long elapsed = System.currentTimeMillis() - start;

            log.info("Portkey health check passed: model={}, latency={}ms", defaultModel, elapsed);
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "model", defaultModel,
                    "response", response.strip(),
                    "latencyMs", elapsed
            ));
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Portkey health check failed: {}", e.getMessage());
            return ResponseEntity.status(503).body(Map.of(
                    "status", "error",
                    "model", defaultModel,
                    "error", e.getMessage() != null ? e.getMessage() : "Unknown error",
                    "latencyMs", elapsed
            ));
        }
    }
}
