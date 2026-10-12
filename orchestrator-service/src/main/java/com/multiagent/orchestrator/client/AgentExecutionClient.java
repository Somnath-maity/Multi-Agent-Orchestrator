package com.multiagent.orchestrator.client;

import com.multiagent.proto.AgentExecutionServiceGrpc;
import com.multiagent.proto.StepRequest;
import com.multiagent.proto.StepResult;
import com.multiagent.proto.StepType;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class AgentExecutionClient {

    @GrpcClient("agent-execution")
    private AgentExecutionServiceGrpc.AgentExecutionServiceBlockingStub stub;

    public StepResult executeStep(
            String stepId,
            String workflowId,
            com.multiagent.orchestrator.model.StepType type,
            String input,
            String promptTemplateId,
            Map<String, String> promptVariables,
            String outputSchema,
            boolean critical
    ) {
        StepType grpcType = switch (type) {
            case PLAN -> StepType.PLAN;
            case LLM_CALL -> StepType.LLM_CALL;
            case TOOL_CALL -> StepType.TOOL_CALL;
            case AGGREGATE -> StepType.AGGREGATE;
        };

        StepRequest.Builder requestBuilder = StepRequest.newBuilder()
                .setStepId(stepId)
                .setWorkflowId(workflowId)
                .setType(grpcType)
                .setInput(input != null ? input : "")
                .setCritical(critical);

        if (promptTemplateId != null) {
            requestBuilder.setPromptTemplateId(promptTemplateId);
        }
        if (promptVariables != null) {
            requestBuilder.putAllPromptVariables(promptVariables);
        }
        if (outputSchema != null) {
            requestBuilder.setOutputSchema(outputSchema);
        }

        log.info("Calling agent-execution: stepId={}, type={}", stepId, type);
        return stub.executeStep(requestBuilder.build());
    }
}
