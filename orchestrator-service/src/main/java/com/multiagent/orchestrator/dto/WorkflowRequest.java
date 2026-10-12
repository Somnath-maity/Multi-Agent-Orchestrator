package com.multiagent.orchestrator.dto;

import jakarta.validation.constraints.NotBlank;

public record WorkflowRequest(
        @NotBlank(message = "Goal must not be blank")
        String goal
) {
}
