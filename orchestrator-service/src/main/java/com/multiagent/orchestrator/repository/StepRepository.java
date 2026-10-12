package com.multiagent.orchestrator.repository;

import com.multiagent.orchestrator.model.Step;
import com.multiagent.orchestrator.model.StepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StepRepository extends JpaRepository<Step, UUID> {

    List<Step> findByWorkflowIdOrderByCreatedAt(UUID workflowId);

    List<Step> findByWorkflowIdAndStatus(UUID workflowId, StepStatus status);
}
