package com.agentops.guardian.governance.context;

import com.agentops.guardian.governance.workflow.WorkflowDefinition;
import com.agentops.guardian.governance.workflow.WorkflowDefinitionRegistry;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WorkflowContextManager {

    private final WorkflowContextHolder contextHolder;
    private final WorkflowDefinitionRegistry workflowDefinitionRegistry;

    public WorkflowContextManager(
            WorkflowContextHolder contextHolder,
            WorkflowDefinitionRegistry workflowDefinitionRegistry
    ) {
        this.contextHolder = contextHolder;
        this.workflowDefinitionRegistry = workflowDefinitionRegistry;
    }

    public WorkflowContext start(
            String agentName,
            WorkflowType workflowType,
            String goal
    ) {
        return start(agentName, workflowType, goal, null);
    }

    public WorkflowContext start(
            String agentName,
            WorkflowType workflowType,
            String goal,
            UUID conversationId
    ) {
        if (contextHolder.getCurrentWorkflow() != null) {
            throw new IllegalStateException("A workflow is already active on this thread.");
        }

        WorkflowDefinition definition = workflowDefinitionRegistry.get(workflowType);

        return contextHolder.startWorkflow(
                agentName,
                workflowType,
                goal,
                definition,
                conversationId
        );
    }

    public WorkflowContext resume(
            String workflowId,
            String agentName,
            WorkflowType workflowType,
            String goal,
            UUID conversationId
    ) {
        if (contextHolder.getCurrentWorkflow() != null) {
            throw new IllegalStateException("A workflow is already active on this thread.");
        }

        WorkflowDefinition definition = workflowDefinitionRegistry.get(workflowType);

        return contextHolder.resumeWorkflow(
                workflowId,
                agentName,
                workflowType,
                goal,
                definition,
                conversationId
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