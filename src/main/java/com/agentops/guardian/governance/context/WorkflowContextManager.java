package com.agentops.guardian.governance.context;

import com.agentops.guardian.governance.workflow.WorkflowDefinition;
import com.agentops.guardian.governance.workflow.WorkflowDefinitionRegistry;
import com.agentops.guardian.governance.workflow.WorkflowType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkflowContextManager {

    private final WorkflowContextHolder contextHolder;
    private final WorkflowDefinitionRegistry workflowDefinitionRegistry;

    public WorkflowContext start(
            String agentName,
            WorkflowType workflowType,
            String goal
    ) {
        if (contextHolder.getCurrentWorkflow() != null) {
            throw new IllegalStateException("A workflow is already active on this thread.");
        }

        WorkflowDefinition definition = workflowDefinitionRegistry.get(workflowType);

        return contextHolder.startWorkflow(
                agentName,
                workflowType,
                goal,
                definition
        );
    }

    public WorkflowContext current() {
        WorkflowContext context = contextHolder.getCurrentWorkflow();

        if (context == null) {
            throw new IllegalStateException("No active workflow context.");
        }

        return context;
    }

    public WorkflowContext complete() {
        WorkflowContext context = current();
        context.complete();
        return context;
    }

    public WorkflowContext fail() {
        WorkflowContext context = current();
        context.fail();
        return context;
    }

    public WorkflowContext block() {
        WorkflowContext context = current();
        context.block();
        return context;
    }

    public void clear() {
        contextHolder.clear();
    }
}