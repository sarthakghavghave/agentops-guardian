package com.agentops.guardian.governance.context;

import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.model.DataTransformation;
import com.agentops.guardian.governance.model.ToolCallEvent;
import com.agentops.guardian.governance.workflow.WorkflowDefinition;
import com.agentops.guardian.governance.workflow.WorkflowType;
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
    private final WorkflowType workflowType;
    private final String goal;
    private final WorkflowDefinition workflowDefinition;
    private final Instant startedAt;

    private Instant completedAt;
    private WorkflowStatus status;

    private final List<ToolCallEvent> toolCalls = new ArrayList<>();
    private final Map<String, Object> data = new HashMap<>();

    private DataClassification currentDataClassification;
    private DataTransformation lastTransformation;

    public WorkflowContext(
            String agentName,
            WorkflowType workflowType,
            String goal,
            WorkflowDefinition workflowDefinition
    ) {
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("Agent name is required.");
        }

        if (workflowType == null) {
            throw new IllegalArgumentException("Workflow type is required.");
        }

        if (goal == null || goal.isBlank()) {
            throw new IllegalArgumentException("Workflow goal is required.");
        }

        if (workflowDefinition == null) {
            throw new IllegalArgumentException("Workflow definition is required.");
        }

        this.workflowId = UUID.randomUUID().toString();
        this.agentName = agentName;
        this.workflowType = workflowType;
        this.goal = goal;
        this.workflowDefinition = workflowDefinition;
        this.startedAt = Instant.now();
        this.status = WorkflowStatus.RUNNING;
    }

    public void addToolCall(ToolCallEvent event) {
        ensureRunning();
        toolCalls.add(event);
    }

    public void complete() {
        ensureRunning();
        status = WorkflowStatus.COMPLETED;
        completedAt = Instant.now();
    }

    public void fail() {
        if (status != WorkflowStatus.RUNNING) {
            return;
        }

        status = WorkflowStatus.FAILED;
        completedAt = Instant.now();
    }

    public void block() {
        if (status != WorkflowStatus.RUNNING) {
            return;
        }

        status = WorkflowStatus.BLOCKED;
        completedAt = Instant.now();
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

    public void markDataAcquired(DataClassification classification) {
        ensureRunning();
        currentDataClassification = classification;
    }

    public void recordTransformation(DataTransformation transformation) {
        ensureRunning();
        lastTransformation = transformation;
        currentDataClassification = transformation.resultClassification();
    }

    private void ensureRunning() {
        if (status != WorkflowStatus.RUNNING) {
            throw new IllegalStateException(
                    "Workflow is no longer running: " + workflowId
            );
        }
    }
}