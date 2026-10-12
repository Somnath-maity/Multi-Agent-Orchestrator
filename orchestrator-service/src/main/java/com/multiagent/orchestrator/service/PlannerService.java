package com.multiagent.orchestrator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multiagent.orchestrator.client.AgentExecutionClient;
import com.multiagent.orchestrator.model.*;
import com.multiagent.orchestrator.repository.StepRepository;
import com.multiagent.orchestrator.repository.WorkflowRepository;
import com.multiagent.proto.StepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlannerService {

    private final StepRepository stepRepository;
    private final WorkflowRepository workflowRepository;
    private final AgentExecutionClient agentExecutionClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public List<Step> generatePlan(Workflow workflow) {
        log.info("Generating LLM plan for workflow id={}, goal='{}'",
                workflow.getId(), workflow.getGoal());

        try {
            return generatePlanViaLlm(workflow);
        } catch (Exception e) {
            log.warn("LLM planning failed, falling back to single-step plan: {}", e.getMessage());
            return generateFallbackPlan(workflow);
        }
    }

    private List<Step> generatePlanViaLlm(Workflow workflow) throws Exception {
        StepResult result = agentExecutionClient.executeStep(
                "plan-" + workflow.getId(),
                workflow.getId().toString(),
                StepType.PLAN,
                workflow.getGoal(),
                "planning",
                Map.of("goal", workflow.getGoal()),
                null,
                false
        );

        if (!result.getSuccess()) {
            throw new RuntimeException("Planning LLM call failed: " + result.getError());
        }

        String planJson = result.getOutput().strip();
        // Strip markdown fences if the LLM wraps the JSON
        if (planJson.startsWith("```")) {
            planJson = planJson.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }

        log.debug("Raw plan JSON: {}", planJson);

        List<Map<String, Object>> rawSteps = objectMapper.readValue(
                planJson, new TypeReference<>() {});

        List<Step> steps = new ArrayList<>();
        // Map from plan index → saved Step entity, used to resolve depends_on
        Map<Integer, Step> indexToStep = new HashMap<>();

        for (int i = 0; i < rawSteps.size(); i++) {
            Map<String, Object> raw = rawSteps.get(i);

            StepType type = StepType.valueOf((String) raw.get("type"));
            String description = (String) raw.getOrDefault("description", "Step " + (i + 1));
            String inputTemplate = (String) raw.getOrDefault("input_template", description);

            Step step = Step.builder()
                    .workflow(workflow)
                    .type(type)
                    .status(StepStatus.PENDING)
                    .input(inputTemplate)
                    .dependsOn("")
                    .build();

            Step saved = stepRepository.save(step);
            indexToStep.put(i, saved);
            steps.add(saved);
        }

        // Second pass: resolve depends_on indices to actual Step UUIDs
        for (int i = 0; i < rawSteps.size(); i++) {
            Map<String, Object> raw = rawSteps.get(i);
            @SuppressWarnings("unchecked")
            List<Integer> depIndices = (List<Integer>) raw.getOrDefault("depends_on", List.of());

            if (!depIndices.isEmpty()) {
                List<UUID> depIds = depIndices.stream()
                        .filter(indexToStep::containsKey)
                        .map(idx -> indexToStep.get(idx).getId())
                        .toList();

                Step step = indexToStep.get(i);
                step.setDependsOnList(depIds);
                stepRepository.save(step);
            }
        }

        workflow.setPlanJson(planJson);
        workflowRepository.save(workflow);

        log.info("LLM plan generated with {} step(s) for workflow id={}",
                steps.size(), workflow.getId());
        return steps;
    }

    private List<Step> generateFallbackPlan(Workflow workflow) {
        log.info("Using fallback single-step plan for workflow id={}", workflow.getId());

        Step step = Step.builder()
                .workflow(workflow)
                .type(StepType.LLM_CALL)
                .status(StepStatus.PENDING)
                .input(workflow.getGoal())
                .dependsOn("")
                .build();

        Step saved = stepRepository.save(step);

        String planJson = """
                [{"type": "LLM_CALL", "description": "Direct response", "depends_on": []}]
                """;
        workflow.setPlanJson(planJson);
        workflowRepository.save(workflow);

        return List.of(saved);
    }
}
