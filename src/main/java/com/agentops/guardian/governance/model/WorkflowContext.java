package com.agentops.guardian.governance.model;

import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
public class WorkflowContext {

    private final String workflowId;
    private final String agentName;
    private final Instant startedAt;
    private Instant completedAt;
    private WorkflowStatus status;
    private final List<ToolCallEvent> toolCalls = new ArrayList<>();
    private final Map<String, Object> data = new HashMap<>();

    public WorkflowContext(String agentName) {
        this.workflowId = UUID.randomUUID().toString();
        this.agentName = agentName;
        this.startedAt = Instant.now();
        this.status = WorkflowStatus.RUNNING;
    }

    public void addToolCall(ToolCallEvent event) {
        if (status != WorkflowStatus.RUNNING) {
            throw new IllegalStateException("Cannot add tool calls to a completed workflow.");
        }
        toolCalls.add(event);
    }

    public void complete() {
        ensureRunning();
        this.status = WorkflowStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void fail() {

        if (status != WorkflowStatus.RUNNING)
            return;

        this.status = WorkflowStatus.FAILED;
        this.completedAt = Instant.now();
    }

    public List<ToolCallEvent> getToolCalls() {
        return List.copyOf(toolCalls);
    }

    public void putData(String key, Object value) {
        ensureRunning();
        data.put(key, value);
    }

    public Object getData(String key) {
        return data.get(key);
    }

    public void removeData(String key) {
        ensureRunning();
        data.remove(key);
    }

    public void clearData() {
        data.clear();
    }

    private void ensureRunning() {

        if (status != WorkflowStatus.RUNNING) {
            throw new IllegalStateException("Workflow is no longer running.");
        }
    }

    public int getToolCallCount() {
        return toolCalls.size();
    }
}