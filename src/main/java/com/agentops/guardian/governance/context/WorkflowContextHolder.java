package com.agentops.guardian.governance.context;

import org.springframework.stereotype.Component;

@Component
public class WorkflowContextHolder {

    private final ThreadLocal<WorkflowContext> context = new ThreadLocal<>();

    public WorkflowContext startWorkflow(String agentName) {
        WorkflowContext workflowContext = new WorkflowContext(agentName);
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