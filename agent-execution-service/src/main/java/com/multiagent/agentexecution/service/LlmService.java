package com.multiagent.agentexecution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multiagent.agentexecution.client.PortkeyClient;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates LLM calls with caching, token budgeting, and optional JSON schema validation.
 */
@Slf4j
@Service
public class LlmService {

    private final PortkeyClient portkeyClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final String CACHE_PREFIX = "llm:cache:";

    @Value("${portkey.default-model}")
    private String defaultModel;

    public LlmService(PortkeyClient portkeyClient, RedisTemplate<String, String> redisTemplate) {
        this.portkeyClient = portkeyClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Execute an LLM call with caching and optional JSON schema validation.
     *
     * @param prompt       the prompt to send
     * @param model        the model to use (falls back to default if null/blank)
     * @param outputSchema optional JSON schema string to validate the output against
     * @return the LLM response content
     */
    public String execute(String prompt, String model, String outputSchema) {
        String effectiveModel = (model == null || model.isBlank()) ? defaultModel : model;
        String cacheKey = CACHE_PREFIX + generateCacheKey(prompt, effectiveModel);

        // 1. Check cache
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Cache hit for key={}", cacheKey);
            return cached;
        }

        // 2. Call LLM
        log.debug("Cache miss -- calling Portkey: model={}, promptLength={}", effectiveModel, prompt.length());
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", prompt)
        );
        String result = portkeyClient.chatCompletionSync(effectiveModel, messages, 0.7);

        // 3. Validate against JSON schema if provided
        if (outputSchema != null && !outputSchema.isBlank()) {
            Set<ValidationMessage> errors = validateJsonSchema(result, outputSchema);
            if (!errors.isEmpty()) {
                log.warn("Schema validation failed on first attempt: {}", errors);

                // Retry once with a repair prompt
                String repairPrompt = buildRepairPrompt(prompt, result, errors);
                result = portkeyClient.chatCompletionSync(effectiveModel,
                        List.of(Map.of("role", "user", "content", repairPrompt)), 0.3);

                Set<ValidationMessage> retryErrors = validateJsonSchema(result, outputSchema);
                if (!retryErrors.isEmpty()) {
                    log.error("Schema validation failed after repair attempt: {}", retryErrors);
                    // TODO: Decide policy -- throw or return best-effort result
                }
            }
        }

        // 4. Cache the result
        redisTemplate.opsForValue().set(cacheKey, result, CACHE_TTL);
        log.debug("Result cached: key={}", cacheKey);

        return result;
    }

    /**
     * Simple token estimation heuristic: ~4 characters per token.
     *
     * @param text the input text
     * @return estimated token count as a string
     */
    public String estimateTokens(String text) {
        int estimated = (text == null) ? 0 : text.length() / 4;
        return String.valueOf(estimated);
    }

    /**
     * Validates a JSON string against a JSON schema.
     */
    private Set<ValidationMessage> validateJsonSchema(String json, String schemaStr) {
        try {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
            JsonSchema schema = factory.getSchema(schemaStr);
            JsonNode node = objectMapper.readTree(json);
            return schema.validate(node);
        } catch (Exception e) {
            log.error("Failed to parse JSON for schema validation: {}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * Builds a repair prompt that includes the original prompt, the invalid output,
     * and the validation errors.
     */
    private String buildRepairPrompt(String originalPrompt, String invalidOutput, Set<ValidationMessage> errors) {
        return """
                The following output was generated but failed JSON schema validation.

                Original prompt:
                %s

                Invalid output:
                %s

                Validation errors:
                %s

                Please regenerate the output, fixing the validation errors. \
                Return ONLY the corrected JSON with no additional text.
                """.formatted(originalPrompt, invalidOutput, errors);
    }

    /**
     * Generates a SHA-256 cache key from the prompt and model.
     */
    private String generateCacheKey(String prompt, String model) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = model + "::" + prompt;
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every JVM
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
