package com.agentops.guardian.governance.context;

import com.agentops.guardian.governance.workflow.WorkflowDefinition;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.springframework.stereotype.Component;

@Component
public class WorkflowContextHolder {

    private final ThreadLocal<WorkflowContext> context = new ThreadLocal<>();

    public WorkflowContext startWorkflow(
            String agentName,
            WorkflowType workflowType,
            String goal,
            WorkflowDefinition workflowDefinition
    ) {
        WorkflowContext workflowContext = new WorkflowContext(
                agentName,
                workflowType,
                goal,
                workflowDefinition
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