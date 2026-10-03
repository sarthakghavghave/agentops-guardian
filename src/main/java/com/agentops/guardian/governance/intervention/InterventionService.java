package com.agentops.guardian.governance.intervention;

import com.agentops.guardian.governance.audit.AuditEvent;
import com.agentops.guardian.governance.audit.AuditEventEntity;
import com.agentops.guardian.governance.audit.AuditEventRepository;
import com.agentops.guardian.governance.audit.AuditEventType;
import com.agentops.guardian.governance.audit.AuditService;
import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.risk.RiskDecision;
import com.agentops.guardian.governance.risk.RiskLevel;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class InterventionService {

    private static final String STALE_REASON =
            "Workflow state or data classification changed after this intervention was created.";

    private final GovernanceInterventionRepository interventionRepository;
    private final AuditEventRepository auditEventRepository;
    private final AuditService auditService;

    public InterventionService(
            GovernanceInterventionRepository interventionRepository,
            AuditEventRepository auditEventRepository,
            AuditService auditService
    ) {
        this.interventionRepository = interventionRepository;
        this.auditEventRepository = auditEventRepository;
        this.auditService = auditService;
    }

    @Transactional
    public GovernanceIntervention createPending(
            WorkflowContext context,
            String toolCallId,
            WorkflowCapability capability,
            RiskDecision decision
    ) {
        if (context == null) {
            throw new IllegalArgumentException("Workflow context is required.");
        }
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("Tool call id is required.");
        }
        if (capability == null || decision == null) {
            throw new IllegalArgumentException("Capability and risk decision are required.");
        }
        if (decision.riskLevel() != RiskLevel.HIGH
            || decision.intervention() != com.agentops.guardian.governance.risk.GovernanceIntervention.REQUIRE_APPROVAL) {
            throw new IllegalArgumentException("Only HIGH risk approval decisions create interventions.");
        }

        GovernanceIntervention intervention = new GovernanceIntervention(
                UUID.randomUUID().toString(),
                context.getWorkflowId(),
                toolCallId,
                context.getAgentName(),
                context.getWorkflowType(),
                capability,
                decision.riskLevel(),
                decision.intervention(),
                GovernanceInterventionStatus.PENDING,
                decision.reason(),
                Instant.now(),
                null,
                null,
                null,
                context.getCurrentNodeId(),
                context.getCurrentState(),
                context.getCurrentDataClassification()
        );

        interventionRepository.save(GovernanceInterventionEntity.from(intervention));
        auditService.record(AuditEvent.interventionLifecycle(intervention, AuditEventType.INTERVENTION_CREATED));
        return intervention;
    }

    @Transactional(readOnly = true)
    public GovernanceIntervention get(String interventionId) {
        return find(interventionId).toDomain();
    }

    @Transactional(readOnly = true)
    public List<GovernanceIntervention> list() {
        return interventionRepository.findAllByOrderByCreatedAtDescInterventionIdAsc().stream()
                .map(GovernanceInterventionEntity::toDomain)
                .toList();
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public GovernanceIntervention approve(String interventionId, String resolvedBy, String resolutionReason) {
        GovernanceInterventionEntity entity = find(interventionId);
        GovernanceIntervention pending = entity.toDomain();
        requirePending(pending);

        if (isStale(pending)) {
            GovernanceIntervention expired = pending.expire(STALE_REASON, Instant.now());
            entity.updateFrom(expired);
            saveResolution(entity);
            auditService.record(AuditEvent.interventionLifecycle(expired, AuditEventType.INTERVENTION_EXPIRED));
            throw new ResponseStatusException(HttpStatus.CONFLICT, STALE_REASON);
        }

        GovernanceIntervention approved = pending.approve(resolvedBy, resolutionReason, Instant.now());
        entity.updateFrom(approved);
        saveResolution(entity);
        auditService.record(AuditEvent.interventionLifecycle(approved, AuditEventType.INTERVENTION_APPROVED));
        return approved;
    }

    @Transactional
    public GovernanceIntervention reject(String interventionId, String resolvedBy, String resolutionReason) {
        GovernanceInterventionEntity entity = find(interventionId);
        GovernanceIntervention pending = entity.toDomain();
        requirePending(pending);

        GovernanceIntervention rejected = pending.reject(resolvedBy, resolutionReason, Instant.now());
        entity.updateFrom(rejected);
        saveResolution(entity);
        auditService.record(AuditEvent.interventionLifecycle(rejected, AuditEventType.INTERVENTION_REJECTED));
        return rejected;
    }

    private GovernanceInterventionEntity find(String interventionId) {
        return interventionRepository.findByInterventionId(interventionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Intervention not found: " + interventionId
                ));
    }

    private void requirePending(GovernanceIntervention intervention) {
        if (intervention.status() != GovernanceInterventionStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Intervention has already been resolved: " + intervention.interventionId()
            );
        }
    }

    private void saveResolution(GovernanceInterventionEntity entity) {
        try {
            interventionRepository.saveAndFlush(entity);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Intervention was resolved concurrently: " + entity.getInterventionId()
            );
        }
    }

    private boolean isStale(GovernanceIntervention intervention) {
        List<AuditEventEntity> events = auditEventRepository
                .findByWorkflowIdOrderByTimestampAscIdAsc(intervention.workflowId());
        if (events.isEmpty()) {
            return true;
        }

        String currentNodeId = latest(events, AuditEventEntity::getNodeAfterId);
        if (currentNodeId == null) {
            currentNodeId = latest(events, AuditEventEntity::getNodeBeforeId);
        }
        WorkflowState currentState = latest(events, AuditEventEntity::getStateAfter);
        if (currentState == null) {
            currentState = latest(events, AuditEventEntity::getStateBefore);
        }
        DataClassification currentClassification = latest(events, AuditEventEntity::getClassificationAfter);
        if (currentClassification == null) {
            currentClassification = latest(events, AuditEventEntity::getClassificationBefore);
        }

        return !intervention.workflowNodeId().equals(currentNodeId)
                || intervention.workflowState() != currentState
                || intervention.classificationAtCreation() != currentClassification
                || currentClassification == DataClassification.RAW_CUSTOMER_DATA;
    }

    private <T> T latest(List<AuditEventEntity> events, java.util.function.Function<AuditEventEntity, T> value) {
        for (int index = events.size() - 1; index >= 0; index--) {
            T candidate = value.apply(events.get(index));
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }
}