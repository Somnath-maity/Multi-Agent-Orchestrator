package com.multiagent.orchestrator.controller;

import com.multiagent.orchestrator.dto.StepEvent;
import com.multiagent.orchestrator.dto.WorkflowRequest;
import com.multiagent.orchestrator.dto.WorkflowResponse;
import com.multiagent.orchestrator.model.Workflow;
import com.multiagent.orchestrator.service.ExecutionService;
import com.multiagent.orchestrator.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/workflows")
@RequiredArgsConstructor
@Slf4j
public class WorkflowController {

    private final WorkflowService workflowService;
    private final ExecutionService executionService;

    /**
     * Active SSE emitters keyed by workflow ID.
     */
    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * POST /workflows - Create a new workflow and kick off async execution.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowResponse createWorkflow(@Valid @RequestBody WorkflowRequest request) {
        Workflow workflow = workflowService.createWorkflow(request.goal());
        log.info("Workflow created id={}, starting async execution", workflow.getId());

        // Kick off execution asynchronously
        executionService.executeWorkflow(workflow.getId());

        return WorkflowResponse.fromEntity(workflow);
    }

    /**
     * GET /workflows/{id} - Retrieve workflow details with steps.
     */
    @GetMapping("/{id}")
    public WorkflowResponse getWorkflow(@PathVariable UUID id) {
        Workflow workflow = workflowService.getWorkflow(id);
        return WorkflowResponse.fromEntity(workflow);
    }

    /**
     * GET /workflows/{id}/events - Subscribe to SSE events for a workflow.
     * Sends real-time step status updates as the workflow executes.
     */
    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@PathVariable UUID id) {
        SseEmitter emitter = new SseEmitter(0L); // No timeout

        emitters.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(id, emitter));
        emitter.onTimeout(() -> removeEmitter(id, emitter));
        emitter.onError(e -> removeEmitter(id, emitter));

        // Send initial state
        try {
            Workflow workflow = workflowService.getWorkflow(id);
            WorkflowResponse response = WorkflowResponse.fromEntity(workflow);
            emitter.send(SseEmitter.event()
                    .name("workflow-state")
                    .data(response));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE state for workflow id={}", id, e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * Listens for StepEvent and broadcasts to all SSE subscribers for that workflow.
     */
    @EventListener
    public void onStepEvent(StepEvent event) {
        var workflowEmitters = emitters.get(event.workflowId());
        if (workflowEmitters == null || workflowEmitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : workflowEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("step-update")
                        .data(event));
            } catch (IOException e) {
                log.debug("Removing failed SSE emitter for workflow id={}", event.workflowId());
                removeEmitter(event.workflowId(), emitter);
            }
        }
    }

    private void removeEmitter(UUID workflowId, SseEmitter emitter) {
        var list = emitters.get(workflowId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(workflowId);
            }
        }
    }
}
