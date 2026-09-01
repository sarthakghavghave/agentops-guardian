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

    private final List<ToolCallEvent> toolCalls = new ArrayList<>();
    private final Map<String, Object> data = new HashMap<>();

    public WorkflowContext(String agentName) {
        this.workflowId = UUID.randomUUID().toString();
        this.agentName = agentName;
        this.startedAt = Instant.now();
    }

    public void addToolCall(ToolCallEvent event) {
        toolCalls.add(event);
    }

    public List<ToolCallEvent> getToolCalls() {
        return List.copyOf(toolCalls);
    }

    public void putData(String key, Object value) {
        data.put(key, value);
    }

    public Object getData(String key) {
        return data.get(key);
    }

    public void removeData(String key) {
        data.remove(key);
    }

    public void clearData() {
        data.clear();
    }
}