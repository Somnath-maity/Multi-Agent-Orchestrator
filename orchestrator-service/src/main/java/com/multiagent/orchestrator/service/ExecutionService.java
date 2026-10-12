package com.multiagent.orchestrator.service;

import com.multiagent.orchestrator.client.AgentExecutionClient;
import com.multiagent.orchestrator.dto.StepEvent;
import com.multiagent.orchestrator.model.*;
import com.multiagent.orchestrator.repository.StepRepository;
import com.multiagent.proto.StepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Manages the execution of workflows using virtual threads.
 * Orchestrates planning, topological sorting, and step execution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionService {

    private final WorkflowService workflowService;
    private final PlannerService plannerService;
    private final AgentExecutionClient agentExecutionClient;
    private final StepRepository stepRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final ExecutorService virtualThreadExecutor =
            Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Executes a workflow asynchronously: plans, sorts topologically, and runs each step.
     */
    public void executeWorkflow(UUID workflowId) {
        virtualThreadExecutor.submit(() -> {
            try {
                doExecuteWorkflow(workflowId);
            } catch (Exception e) {
                log.error("Workflow execution failed for id={}", workflowId, e);
                workflowService.updateStatus(workflowId, WorkflowStatus.FAILED);
            }
        });
    }

    private void doExecuteWorkflow(UUID workflowId) {
        log.info("Starting execution for workflow id={}", workflowId);

        // Phase 1: Planning
        workflowService.updateStatus(workflowId, WorkflowStatus.PLANNING);
        Workflow workflow = workflowService.getWorkflow(workflowId);
        List<Step> steps = plannerService.generatePlan(workflow);

        // Phase 2: Executing
        workflowService.updateStatus(workflowId, WorkflowStatus.EXECUTING);

        // Topological sort of steps based on dependencies
        List<Step> sortedSteps = topologicalSort(steps);

        // Execute steps in order
        Set<UUID> completedStepIds = new HashSet<>();
        boolean allSucceeded = true;

        for (Step step : sortedSteps) {
            // Check that all dependencies are satisfied
            List<UUID> deps = step.getDependsOnList();
            if (!completedStepIds.containsAll(deps)) {
                log.warn("Skipping step id={} — unmet dependencies", step.getId());
                step.setStatus(StepStatus.SKIPPED);
                stepRepository.save(step);
                publishStepEvent(step, workflowId);
                allSucceeded = false;
                continue;
            }

            // Execute the step
            executeStep(step, workflowId);

            if (step.getStatus() == StepStatus.SUCCEEDED) {
                completedStepIds.add(step.getId());
            } else {
                allSucceeded = false;
            }
        }

        // Phase 3: Complete
        WorkflowStatus finalStatus = allSucceeded ? WorkflowStatus.COMPLETED : WorkflowStatus.FAILED;
        workflowService.updateStatus(workflowId, finalStatus);
        log.info("Workflow id={} finished with status={}", workflowId, finalStatus);
    }

    private void executeStep(Step step, UUID workflowId) {
        log.info("Executing step id={} type={}", step.getId(), step.getType());

        step.setStatus(StepStatus.RUNNING);
        step.setAttempt(step.getAttempt() + 1);
        stepRepository.save(step);
        publishStepEvent(step, workflowId);

        try {
            StepResult result = agentExecutionClient.executeStep(
                    step.getId().toString(),
                    workflowId.toString(),
                    step.getType(),
                    step.getInput(),
                    null,
                    null,
                    null,
                    false
            );

            if (result.getSuccess()) {
                step.setStatus(StepStatus.SUCCEEDED);
                step.setOutput(result.getOutput());
                step.setEvalScore(result.getEvalScore());
                log.info("Step id={} succeeded, tokens={}, model={}",
                        step.getId(), result.getTokensUsed(), result.getModelUsed());
            } else {
                step.setStatus(StepStatus.FAILED);
                step.setOutput(result.getError());
                log.warn("Step id={} failed: {}", step.getId(), result.getError());
            }
        } catch (Exception e) {
            step.setStatus(StepStatus.FAILED);
            step.setOutput("gRPC call failed: " + e.getMessage());
            log.error("Step id={} gRPC call failed", step.getId(), e);
        }

        stepRepository.save(step);
        publishStepEvent(step, workflowId);
    }

    /**
     * Performs a topological sort on the steps based on their dependency graph.
     */
    private List<Step> topologicalSort(List<Step> steps) {
        if (steps.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, Step> stepMap = steps.stream()
                .collect(Collectors.toMap(Step::getId, s -> s));

        Map<UUID, Integer> inDegree = new HashMap<>();
        Map<UUID, List<UUID>> adjacency = new HashMap<>();

        for (Step step : steps) {
            inDegree.putIfAbsent(step.getId(), 0);
            adjacency.putIfAbsent(step.getId(), new ArrayList<>());
            for (UUID dep : step.getDependsOnList()) {
                adjacency.computeIfAbsent(dep, k -> new ArrayList<>()).add(step.getId());
                inDegree.merge(step.getId(), 1, Integer::sum);
            }
        }

        // Kahn's algorithm
        Queue<UUID> queue = new ArrayDeque<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<Step> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            Step step = stepMap.get(current);
            if (step != null) {
                sorted.add(step);
            }
            for (UUID neighbor : adjacency.getOrDefault(current, Collections.emptyList())) {
                int newDegree = inDegree.merge(neighbor, -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (sorted.size() != steps.size()) {
            log.warn("Topological sort detected a cycle in the step dependency graph");
        }

        return sorted;
    }

    private void publishStepEvent(Step step, UUID workflowId) {
        StepEvent event = new StepEvent(
                step.getId(),
                workflowId,
                step.getStatus(),
                step.getType(),
                step.getOutput(),
                LocalDateTime.now()
        );
        eventPublisher.publishEvent(event);
    }
}
