package com.agentops.guardian.service;

import com.agentops.guardian.governance.audit.AuditEventEntity;
import com.agentops.guardian.governance.audit.AuditEventType;
import com.agentops.guardian.governance.audit.AuditEventRepository;
import com.agentops.guardian.governance.audit.dto.WorkflowSummaryResponse;
import com.agentops.guardian.governance.audit.dto.WorkflowTrajectoryEvent;
import com.agentops.guardian.governance.audit.dto.WorkflowTrajectoryResponse;
import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.risk.GovernanceIntervention;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkflowTrajectoryService {

    private final AuditEventRepository repository;

    public WorkflowTrajectoryService(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<WorkflowSummaryResponse> getSummaries() {
        Map<String, List<AuditEventEntity>> groupedEvents = new LinkedHashMap<>();
        repository.findAllByOrderByWorkflowIdAscTimestampAscIdAsc()
                .forEach(event -> groupedEvents
                        .computeIfAbsent(event.getWorkflowId(), ignored -> new ArrayList<>())
                        .add(event));

        return groupedEvents.values().stream()
                .map(this::summarize)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowTrajectoryResponse getTrajectory(String workflowId) {
        List<AuditEventEntity> events = repository.findByWorkflowIdOrderByTimestampAscIdAsc(workflowId);
        if (events.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found: " + workflowId);
        }

        WorkflowSummaryResponse summary = summarize(events);
        return new WorkflowTrajectoryResponse(
                summary.workflowId(),
                summary.conversationId(),
                summary.agentName(),
                summary.workflowType(),
                summary.startedAt(),
                summary.lastEventAt(),
                summary.currentNodeId(),
                summary.currentState(),
                summary.eventCount(),
                events.stream().map(WorkflowTrajectoryEvent::from).toList()
        );
    }

    private WorkflowSummaryResponse summarize(List<AuditEventEntity> events) {
        AuditEventEntity firstEvent = events.getFirst();
        AuditEventEntity lastEvent = events.getLast();
        Map<String, ActionDecision> decisions = new HashMap<>();
        for (AuditEventEntity event : events) {
            if (event.getToolCallId() == null) {
                continue;
            }
            ActionDecision decision = decisions.computeIfAbsent(
                    event.getToolCallId(),
                    ignored -> new ActionDecision()
            );
            if (event.getEventType() == AuditEventType.POLICY_DECISION
                    && event.getPolicyDecision() != null) {
                decision.policyDecision = event.getPolicyDecision();
            }
            if (event.getEventType() == AuditEventType.RISK_ASSESSMENT
                    && event.getIntervention() != null) {
                decision.intervention = event.getIntervention();
            }
        }
        long blockedActionCount = decisions.values().stream()
                .filter(ActionDecision::isBlocked)
                .count();
        long allowedActionCount = decisions.values().stream()
                .filter(ActionDecision::isAllowedWithoutApproval)
                .count();

        return new WorkflowSummaryResponse(
                firstEvent.getWorkflowId(),
                events.stream()
                        .map(AuditEventEntity::getConversationId)
                        .filter(conversationId -> conversationId != null)
                        .findFirst()
                        .orElse(null),
                firstEvent.getAgentName(),
                firstEvent.getWorkflowType(),
                firstEvent.getTimestamp(),
                lastEvent.getTimestamp(),
                latestNodeId(events),
                latestState(events),
                events.size(),
                allowedActionCount,
                blockedActionCount
        );
    }

    private static final class ActionDecision {
        private GovernanceDecision.DecisionType policyDecision;
        private GovernanceIntervention intervention;

        private boolean isBlocked() {
            return policyDecision == GovernanceDecision.DecisionType.BLOCK
                    || intervention == GovernanceIntervention.BLOCK;
        }

        private boolean isAllowedWithoutApproval() {
            return policyDecision == GovernanceDecision.DecisionType.ALLOW
                    && intervention != GovernanceIntervention.REQUIRE_APPROVAL
                    && intervention != GovernanceIntervention.BLOCK;
        }
    }

    private String latestNodeId(List<AuditEventEntity> events) {
        String nodeAfterId = latestValue(events, AuditEventEntity::getNodeAfterId);
        return nodeAfterId != null ? nodeAfterId : latestValue(events, AuditEventEntity::getNodeBeforeId);
    }

    private WorkflowState latestState(List<AuditEventEntity> events) {
        WorkflowState stateAfter = latestValue(events, AuditEventEntity::getStateAfter);
        return stateAfter != null ? stateAfter : latestValue(events, AuditEventEntity::getStateBefore);
    }

    private <T> T latestValue(List<AuditEventEntity> events, java.util.function.Function<AuditEventEntity, T> value) {
        for (int index = events.size() - 1; index >= 0; index--) {
            T latest = value.apply(events.get(index));
            if (latest != null) {
                return latest;
            }
        }
        return null;
    }
}