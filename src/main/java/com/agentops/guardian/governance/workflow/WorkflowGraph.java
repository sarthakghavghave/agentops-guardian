package com.agentops.guardian.governance.workflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WorkflowGraph {

    public static final String START_NODE_ID = "START";

    private final Map<String, WorkflowNode> nodes;
    private final Set<WorkflowTransition> transitions;

    public WorkflowGraph(
            List<WorkflowNode> nodes,
            Set<WorkflowTransition> transitions
    ) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("Workflow graph must contain nodes.");
        }

        if (transitions == null) {
            throw new IllegalArgumentException("Workflow graph transitions are required.");
        }

        Map<String, WorkflowNode> nodeMap = new LinkedHashMap<>();
        for (WorkflowNode node : nodes) {
            if (node == null) {
                throw new IllegalArgumentException("Workflow graph node cannot be null.");
            }
            if (nodeMap.putIfAbsent(node.id(), node) != null) {
                throw new IllegalArgumentException("Duplicate workflow node id: " + node.id());
            }
        }
        this.nodes = Collections.unmodifiableMap(nodeMap);

        for (WorkflowTransition transition : transitions) {
            if (transition == null) {
                throw new IllegalArgumentException("Workflow graph transition cannot be null.");
            }
        }
        this.transitions = Set.copyOf(transitions);

        validateTransitions();
    }

    public static WorkflowGraph customerReportingGraph() {
        return new WorkflowGraph(
                List.of(
                        new WorkflowNode(START_NODE_ID),
                        new WorkflowNode("CUSTOMER_DATA", WorkflowCapability.READ_CUSTOMER_DATA),
                        new WorkflowNode("REPORT", WorkflowCapability.GENERATE_REPORT),
                        new WorkflowNode("DELIVERY", WorkflowCapability.SEND_EMAIL)
                ),
                Set.of(
                        new WorkflowTransition(START_NODE_ID, "CUSTOMER_DATA"),
                        new WorkflowTransition("CUSTOMER_DATA", "REPORT"),
                        new WorkflowTransition("REPORT", "DELIVERY")
                )
        );
    }

    public boolean containsNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return false;
        }
        return nodes.containsKey(nodeId);
    }

    public WorkflowNode getNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("Workflow node id is required.");
        }

        WorkflowNode node = nodes.get(nodeId);

        if (node == null) {
            throw new IllegalArgumentException("Unknown workflow node: " + nodeId);
        }

        return node;
    }

    public boolean canTransition(String fromNodeId, String toNodeId) {
        if (fromNodeId == null || fromNodeId.isBlank() || toNodeId == null || toNodeId.isBlank()) {
            return false;
        }
        return transitions.contains(
                new WorkflowTransition(fromNodeId, toNodeId)
        );
    }

    public Set<WorkflowTransition> getTransitions() {
        return transitions;
    }

    public List<WorkflowNode> getNodes() {
        return List.copyOf(nodes.values());
    }

    private void validateTransitions() {
        for (WorkflowTransition transition : transitions) {
            if (!nodes.containsKey(transition.fromNodeId())) {
                throw new IllegalArgumentException("Transition references unknown source node: " + transition.fromNodeId());
            }

            if (!nodes.containsKey(transition.toNodeId())) {
                throw new IllegalArgumentException("Transition references unknown target node: " + transition.toNodeId());
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<WorkflowNode> nodes = new ArrayList<>();
        private final Set<WorkflowTransition> transitions = new LinkedHashSet<>();

        public Builder addNode(WorkflowNode node) {
            if (node == null) {
                throw new IllegalArgumentException("Node cannot be null.");
            }
            this.nodes.add(node);
            return this;
        }

        public Builder addNode(String id) {
            return addNode(new WorkflowNode(id));
        }

        public Builder addNode(String id, WorkflowCapability capability) {
            return addNode(new WorkflowNode(id, capability));
        }

        public Builder addTransition(String fromNodeId, String toNodeId) {
            return addTransition(new WorkflowTransition(fromNodeId, toNodeId));
        }

        public Builder addTransition(WorkflowTransition transition) {
            if (transition == null) {
                throw new IllegalArgumentException("Transition cannot be null.");
            }
            this.transitions.add(transition);
            return this;
        }

        public WorkflowGraph build() {
            return new WorkflowGraph(nodes, transitions);
        }
    }
}