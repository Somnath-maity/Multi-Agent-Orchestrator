package com.multiagent.orchestrator.service;

import com.multiagent.orchestrator.model.Step;
import com.multiagent.orchestrator.model.Workflow;
import com.multiagent.orchestrator.model.WorkflowStatus;
import com.multiagent.orchestrator.repository.StepRepository;
import com.multiagent.orchestrator.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final StepRepository stepRepository;

    /**
     * Creates a new workflow with PENDING status.
     */
    @Transactional
    public Workflow createWorkflow(String goal) {
        Workflow workflow = Workflow.builder()
                .goal(goal)
                .status(WorkflowStatus.PENDING)
                .build();
        Workflow saved = workflowRepository.save(workflow);
        log.info("Created workflow id={} for goal='{}'", saved.getId(), goal);
        return saved;
    }

    /**
     * Retrieves a workflow by ID, including its steps.
     */
    @Transactional(readOnly = true)
    public Workflow getWorkflow(UUID id) {
        Workflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + id));
        // Force initialization of lazy-loaded steps
        workflow.getSteps().size();
        return workflow;
    }

    /**
     * Updates the status of a workflow.
     */
    @Transactional
    public Workflow updateStatus(UUID id, WorkflowStatus status) {
        Workflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + id));
        workflow.setStatus(status);
        log.info("Workflow id={} status updated to {}", id, status);
        return workflowRepository.save(workflow);
    }

    /**
     * Returns all steps for a given workflow, ordered by creation time.
     */
    @Transactional(readOnly = true)
    public List<Step> getSteps(UUID workflowId) {
        return stepRepository.findByWorkflowIdOrderByCreatedAt(workflowId);
    }
}
