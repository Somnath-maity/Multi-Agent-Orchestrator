package com.multiagent.orchestrator.dto;

import com.multiagent.orchestrator.model.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record WorkflowResponse(
        UUID id,
        String goal,
        WorkflowStatus status,
        String planJson,
        LocalDateTime createdAt,
        List<StepResponse> steps
) {

    public record StepResponse(
            UUID id,
            StepType type,
            StepStatus status,
            String input,
            String output,
            int attempt,
            Double evalScore
    ) {
        public static StepResponse fromEntity(Step step) {
            return new StepResponse(
                    step.getId(),
                    step.getType(),
                    step.getStatus(),
                    step.getInput(),
                    step.getOutput(),
                    step.getAttempt(),
                    step.getEvalScore()
            );
        }
    }

    public static WorkflowResponse fromEntity(Workflow workflow) {
        List<StepResponse> stepResponses = workflow.getSteps() != null
                ? workflow.getSteps().stream()
                        .map(StepResponse::fromEntity)
                        .toList()
                : Collections.emptyList();

        return new WorkflowResponse(
                workflow.getId(),
                workflow.getGoal(),
                workflow.getStatus(),
                workflow.getPlanJson(),
                workflow.getCreatedAt(),
                stepResponses
        );
    }
}
