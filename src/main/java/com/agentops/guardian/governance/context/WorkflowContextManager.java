package com.agentops.guardian.governance.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkflowContextManager {

    private final WorkflowContextHolder contextHolder;

    public WorkflowContext start(String agentName) {

        if (contextHolder.getCurrentWorkflow() != null) {
            throw new IllegalStateException("A workflow is already active on this thread.");
        }

        return contextHolder.startWorkflow(agentName);
    }

    public WorkflowContext current() {

        WorkflowContext context = contextHolder.getCurrentWorkflow();

        if (context == null) {
            throw new IllegalStateException("No active workflow.");
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

    public void clear() {
        contextHolder.clear();
    }
}