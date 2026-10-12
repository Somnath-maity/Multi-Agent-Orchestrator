package com.multiagent.agentexecution.grpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multiagent.agentexecution.model.PromptTemplate;
import com.multiagent.agentexecution.service.EvalService;
import com.multiagent.agentexecution.service.LlmService;
import com.multiagent.agentexecution.service.PromptTemplateRegistry;
import com.multiagent.agentexecution.service.ToolService;
// TODO: These imports reference proto-generated classes. They will be available after running
//       `mvn compile` which triggers protobuf-maven-plugin to generate from ../proto/*.proto.
import com.multiagent.proto.AgentExecutionServiceGrpc;
import com.multiagent.proto.StepRequest;
import com.multiagent.proto.StepResult;
import com.multiagent.proto.StepType;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * gRPC server implementation for the AgentExecutionService.
 * Handles step execution requests from the Orchestrator service.
 *
 * NOTE: The proto-generated base class (AgentExecutionServiceGrpc.AgentExecutionServiceImplBase)
 * and message types (StepRequest, StepResult, StepType) are generated at build time by the
 * protobuf-maven-plugin from {@code ../proto/agent_execution.proto}.
 */
@Slf4j
@GrpcService
public class AgentExecutionGrpcService extends AgentExecutionServiceGrpc.AgentExecutionServiceImplBase {

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(1);

    private final LlmService llmService;
    private final ToolService toolService;
    private final EvalService evalService;
    private final PromptTemplateRegistry templateRegistry;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${portkey.default-model}")
    private String defaultModel;

    public AgentExecutionGrpcService(
            LlmService llmService,
            ToolService toolService,
            EvalService evalService,
            PromptTemplateRegistry templateRegistry,
            RedisTemplate<String, String> redisTemplate
    ) {
        this.llmService = llmService;
        this.toolService = toolService;
        this.evalService = evalService;
        this.templateRegistry = templateRegistry;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void executeStep(StepRequest request, StreamObserver<StepResult> responseObserver) {
        String stepId = request.getStepId();
        log.info("Received executeStep: stepId={}, type={}, workflowId={}",
                stepId, request.getType(), request.getWorkflowId());

        try {
            // 1. Check idempotency key
            String idempotencyKey = IDEMPOTENCY_PREFIX + stepId;
            String cachedResult = redisTemplate.opsForValue().get(idempotencyKey);
            if (cachedResult != null) {
                log.info("Idempotent hit for stepId={}", stepId);
                StepResult cached = deserializeStepResult(cachedResult, stepId);
                responseObserver.onNext(cached);
                responseObserver.onCompleted();
                return;
            }

            // 2. Execute based on step type
            String output;
            String modelUsed = defaultModel;
            double evalScore = 0.0;

            switch (request.getType()) {
                case PLAN, LLM_CALL -> {
                    String prompt = renderPrompt(request);

                    if (request.getCritical() && request.getType() == StepType.LLM_CALL) {
                        // Use eval service for critical LLM calls
                        int numCandidates = request.getMaxCandidates() > 0 ? request.getMaxCandidates() : 3;
                        output = evalService.evaluateAndSelect(
                                prompt, modelUsed, numCandidates, request.getOutputSchema());
                        evalScore = 1.0; // TODO: Return actual eval score from EvalService
                    } else {
                        output = llmService.execute(prompt, modelUsed, request.getOutputSchema());
                    }
                }
                case TOOL_CALL -> {
                    // Parse tool name and args from the input JSON
                    JsonNode inputNode = objectMapper.readTree(request.getInput());
                    String toolName = inputNode.get("tool_name").asText();
                    String argsJson = inputNode.has("args") ? inputNode.get("args").toString() : "{}";
                    output = toolService.executeTool(toolName, argsJson);
                    modelUsed = "none";
                }
                case AGGREGATE -> {
                    String aggregationPrompt = "Synthesize and aggregate the following inputs into a coherent response:\n\n"
                            + request.getInput();
                    output = llmService.execute(aggregationPrompt, modelUsed, request.getOutputSchema());
                }
                default -> {
                    output = "";
                    log.warn("Unhandled step type: {}", request.getType());
                }
            }

            // 3. Build and return result
            int tokensUsed = Integer.parseInt(llmService.estimateTokens(output));
            StepResult result = StepResult.newBuilder()
                    .setStepId(stepId)
                    .setSuccess(true)
                    .setOutput(output)
                    .setEvalScore(evalScore)
                    .setTokensUsed(tokensUsed)
                    .setModelUsed(modelUsed)
                    .build();

            // 4. Store in idempotency cache
            redisTemplate.opsForValue().set(idempotencyKey, result.getOutput(), IDEMPOTENCY_TTL);

            responseObserver.onNext(result);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Step execution failed: stepId={}, error={}", stepId, e.getMessage(), e);
            StepResult errorResult = StepResult.newBuilder()
                    .setStepId(stepId)
                    .setSuccess(false)
                    .setError(e.getMessage() != null ? e.getMessage() : "Unknown error")
                    .build();
            responseObserver.onNext(errorResult);
            responseObserver.onCompleted();
        }
    }

    /**
     * Renders a prompt from template variables or falls back to raw input.
     */
    private String renderPrompt(StepRequest request) {
        if (request.getPromptTemplateId() != null && !request.getPromptTemplateId().isBlank()) {
            PromptTemplate template = templateRegistry.get(request.getPromptTemplateId())
                    .orElseGet(() -> {
                        log.warn("Template '{}' not found, using raw input as template",
                                request.getPromptTemplateId());
                        return PromptTemplate.builder()
                                .id(request.getPromptTemplateId())
                                .template(request.getInput())
                                .build();
                    });
            return template.render(request.getPromptVariablesMap());
        }
        return request.getInput();
    }

    /**
     * Deserializes a cached result string back into a StepResult.
     * For the idempotency cache, we store only the output string.
     */
    private StepResult deserializeStepResult(String cachedOutput, String stepId) {
        return StepResult.newBuilder()
                .setStepId(stepId)
                .setSuccess(true)
                .setOutput(cachedOutput)
                .build();
    }
}
