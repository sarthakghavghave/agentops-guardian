package com.agentops.guardian.governance.context;

import com.agentops.guardian.governance.workflow.WorkflowDefinition;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WorkflowContextHolder {

    private final ThreadLocal<WorkflowContext> context = new ThreadLocal<>();

    public WorkflowContext startWorkflow(
            String agentName,
            WorkflowType workflowType,
            String goal,
            WorkflowDefinition workflowDefinition
    ) {
        return startWorkflow(agentName, workflowType, goal, workflowDefinition, null);
    }

    public WorkflowContext startWorkflow(
            String agentName,
            WorkflowType workflowType,
            String goal,
            WorkflowDefinition workflowDefinition,
            UUID conversationId
    ) {
        WorkflowContext workflowContext = new WorkflowContext(
                agentName,
                workflowType,
                goal,
                workflowDefinition,
                conversationId
        );

        context.set(workflowContext);

        return workflowContext;
    }

    public WorkflowContext getCurrentWorkflow() {
        return context.get();
    }

    public void clear() {
        context.remove();
    }
}