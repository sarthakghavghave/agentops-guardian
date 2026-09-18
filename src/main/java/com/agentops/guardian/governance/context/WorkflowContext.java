package com.agentops.guardian.governance.context;

import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.model.DataTransformation;
import com.agentops.guardian.governance.model.ToolCallEvent;
import com.agentops.guardian.governance.model.WorkflowAction;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowDefinition;
import com.agentops.guardian.governance.workflow.WorkflowGraph;
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

    public static final String START_NODE_ID = WorkflowGraph.START_NODE_ID;

    private final String workflowId;
    private final String agentName;
    private final WorkflowType workflowType;
    private final String goal;
    private final WorkflowDefinition workflowDefinition;
    private final Instant startedAt;

    private Instant completedAt;
    private WorkflowStatus status;
    private WorkflowState currentState;
    private String currentNodeId;

    private final List<ToolCallEvent> toolCalls = new ArrayList<>();
    private final List<WorkflowAction> actions = new ArrayList<>();
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
        this.currentState = WorkflowState.STARTED;
        this.currentNodeId = START_NODE_ID;
    }

    public void addToolCall(ToolCallEvent event) {
        ensureRunning();
        if (event == null) {
            throw new IllegalArgumentException("Tool call event cannot be null.");
        }
        toolCalls.add(event);
    }

    public void addAction(WorkflowAction action) {
        ensureRunning();
        if (action == null) {
            throw new IllegalArgumentException("Workflow action cannot be null.");
        }
        actions.add(action);
    }

    public List<WorkflowAction> getActions() {
        return List.copyOf(actions);
    }

    public int nextActionSequence() {
        return actions.size() + 1;
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
        ensureRunning();
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

    public void advanceTo(String nodeId, WorkflowState state) {
        ensureRunning();

        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("Workflow node ID is required.");
        }

        if (state == null) {
            throw new IllegalArgumentException("Workflow state is required.");
        }

        if (!workflowDefinition.graph().containsNode(nodeId)) {
            throw new IllegalArgumentException("Unknown workflow node: " + nodeId);
        }

        if (!canTransitionTo(nodeId)) {
            throw new IllegalStateException("Invalid workflow transition from " + currentNodeId + " to " + nodeId);
        }

        this.currentNodeId = nodeId;
        this.currentState = state;
    }

    public boolean canTransitionTo(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return false;
        }
        return workflowDefinition.graph().canTransition(currentNodeId, nodeId);
    }

    public void advanceAfterCapability(WorkflowCapability capability) {
        ensureRunning();

        if (capability == null) {
            throw new IllegalArgumentException("Workflow capability is required.");
        }

        if (!workflowDefinition.allows(capability)) {
            throw new IllegalArgumentException("Capability is not permitted for this workflow: " + capability);
        }

        String targetNodeId;
        WorkflowState targetState;

        switch (capability) {
            case READ_CUSTOMER_DATA -> {
                targetNodeId = "CUSTOMER_DATA";
                targetState = WorkflowState.DATA_ACQUIRED;
            }
            case GENERATE_REPORT -> {
                targetNodeId = "REPORT";
                targetState = WorkflowState.REPORT_GENERATED;
            }
            case SEND_EMAIL -> {
                targetNodeId = "DELIVERY";
                targetState = WorkflowState.DELIVERY_REQUESTED;
            }
            default -> throw new IllegalArgumentException("Unsupported capability transition: " + capability);
        }

        advanceTo(targetNodeId, targetState);
    }

    private void ensureRunning() {
        if (status != WorkflowStatus.RUNNING) {
            throw new IllegalStateException("Workflow is no longer running: " + workflowId);
        }
    }
}