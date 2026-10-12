package com.multiagent.orchestrator.dto;

import com.multiagent.orchestrator.model.StepStatus;
import com.multiagent.orchestrator.model.StepType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event published when a step changes state. Used for SSE streaming to clients.
 */
public record StepEvent(
        UUID stepId,
        UUID workflowId,
        StepStatus status,
        StepType type,
        String output,
        LocalDateTime timestamp
) {
}
