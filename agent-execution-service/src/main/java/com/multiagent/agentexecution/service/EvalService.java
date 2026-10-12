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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles multi-candidate generation and re-ranking for critical workflow steps.
 * Generates multiple candidates, scores them via heuristics and a judge model,
 * and returns the top-scored candidate.
 */
@Slf4j
@Service
public class EvalService {

    private final PortkeyClient portkeyClient;
    private final ObjectMapper objectMapper;

    @Value("${portkey.strong-model}")
    private String strongModel;

    public EvalService(PortkeyClient portkeyClient) {
        this.portkeyClient = portkeyClient;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Generates multiple candidate responses, scores them, and returns the best one.
     *
     * @param prompt        the prompt to generate candidates for
     * @param model         the model to use for candidate generation
     * @param numCandidates number of candidates to generate
     * @param outputSchema  optional JSON schema for validation scoring
     * @return the top-scored candidate response
     */
    public String evaluateAndSelect(String prompt, String model, int numCandidates, String outputSchema) {
        log.info("Eval: generating {} candidates with model={}", numCandidates, model);

        // 1. Generate candidates
        List<String> candidates = new ArrayList<>();
        for (int i = 0; i < numCandidates; i++) {
            try {
                List<Map<String, String>> messages = List.of(
                        Map.of("role", "user", "content", prompt)
                );
                // Use slightly varied temperature for diversity
                double temperature = 0.7 + (i * 0.1);
                String candidate = portkeyClient.chatCompletionSync(model, messages, Math.min(temperature, 1.5));
                candidates.add(candidate);
            } catch (Exception e) {
                log.warn("Failed to generate candidate {}: {}", i, e.getMessage());
            }
        }

        if (candidates.isEmpty()) {
            throw new RuntimeException("Failed to generate any candidates for eval");
        }

        // 2. Score each candidate
        record ScoredCandidate(String content, double score) {}

        List<ScoredCandidate> scored = new ArrayList<>();
        for (String candidate : candidates) {
            double heuristicScore = scoreHeuristic(candidate, outputSchema);
            double judgeScore = scoreWithJudge(prompt, candidate);
            double totalScore = (heuristicScore * 0.4) + (judgeScore * 0.6);

            log.info("Eval candidate: heuristic={}, judge={}, total={}, length={}",
                    heuristicScore, judgeScore, totalScore, candidate.length());

            scored.add(new ScoredCandidate(candidate, totalScore));
        }

        // 3. Select the top-scored candidate
        ScoredCandidate best = scored.stream()
                .max(Comparator.comparingDouble(ScoredCandidate::score))
                .orElseThrow();

        log.info("Eval complete: selected candidate with score={}", best.score());
        return best.content();
    }

    /**
     * Cheap heuristic scoring: checks schema compliance and output length sanity.
     * Returns a score between 0.0 and 1.0.
     */
    private double scoreHeuristic(String candidate, String outputSchema) {
        double score = 0.0;

        // Length sanity check: penalize very short or very long outputs
        int length = candidate.length();
        if (length >= 10 && length <= 50_000) {
            score += 0.5;
        } else if (length > 0) {
            score += 0.2;
        }

        // Schema compliance check
        if (outputSchema != null && !outputSchema.isBlank()) {
            try {
                JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
                JsonSchema schema = factory.getSchema(outputSchema);
                JsonNode node = objectMapper.readTree(candidate);
                Set<ValidationMessage> errors = schema.validate(node);
                score += errors.isEmpty() ? 0.5 : 0.0;
            } catch (Exception e) {
                log.debug("Heuristic schema check failed for candidate: {}", e.getMessage());
                // Non-JSON output with a schema requirement scores 0 for compliance
            }
        } else {
            // No schema to validate against -- give full marks for compliance
            score += 0.5;
        }

        return score;
    }

    /**
     * Scores a candidate using the strong judge model.
     * Returns a score between 0.0 and 1.0.
     */
    private double scoreWithJudge(String originalPrompt, String candidate) {
        try {
            String judgePrompt = """
                    You are an expert evaluator. Rate the following response on a scale of 0 to 10 \
                    based on accuracy, completeness, and relevance to the original prompt.

                    Original prompt:
                    %s

                    Response to evaluate:
                    %s

                    Return ONLY a single integer between 0 and 10. No explanation.
                    """.formatted(originalPrompt, candidate);

            List<Map<String, String>> messages = List.of(
                    Map.of("role", "user", "content", judgePrompt)
            );

            String scoreStr = portkeyClient.chatCompletionSync(strongModel, messages, 0.0);
            int rawScore = Integer.parseInt(scoreStr.strip());
            return Math.clamp(rawScore / 10.0, 0.0, 1.0);
        } catch (Exception e) {
            log.warn("Judge scoring failed, defaulting to 0.5: {}", e.getMessage());
            return 0.5;
        }
    }
}
