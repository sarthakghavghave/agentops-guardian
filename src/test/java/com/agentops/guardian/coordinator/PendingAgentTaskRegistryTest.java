package com.agentops.guardian.coordinator;

import com.agentops.guardian.coordinator.dto.AgentCoordinatorRequest;
import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.intervention.GovernanceIntervention;
import com.agentops.guardian.governance.intervention.GovernanceInterventionStatus;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.risk.RiskLevel;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PendingAgentTaskRegistryTest {

    @Test
    void taskIsConversationBoundAndCanBeClaimedOnlyOnce() {
        UUID conversationId = UUID.randomUUID();
        GovernanceIntervention intervention = pendingIntervention(conversationId);
        PendingAgentTaskRegistry registry = new PendingAgentTaskRegistry();

        registry.register(
                intervention,
                new AgentCoordinatorRequest("Send this email", "customer@example.test", null),
                AgentCoordinator.IntentAction.EMAIL_REQUEST,
                "EMAIL",
                "report-1",
                "{}"
        );

        PendingAgentTaskRegistry.Claim claim = registry.claim(intervention.interventionId());
        assertEquals(PendingAgentTaskRegistry.ClaimStatus.FOUND, claim.status());
        assertEquals(conversationId, claim.task().conversationId());
        assertEquals(conversationId.toString(), claim.task().request().conversationId());
        assertEquals("report-1", claim.task().reportArtifactId());
        assertEquals("Send this email", claim.task().request().message());
        assertEquals("{}", claim.task().arguments());
        assertNotNull(claim.task().expiresAt());
        assertEquals(
                PendingAgentTaskRegistry.ClaimStatus.MISSING,
                registry.claim(intervention.interventionId()).status()
        );
    }

    @Test
    void terminatedTaskCannotBeClaimed() {
        UUID conversationId = UUID.randomUUID();
        GovernanceIntervention intervention = pendingIntervention(conversationId);
        PendingAgentTaskRegistry registry = new PendingAgentTaskRegistry();
        registry.register(
                intervention,
                new AgentCoordinatorRequest("Send this email", "customer@example.test", null),
                AgentCoordinator.IntentAction.EMAIL_REQUEST,
                "EMAIL",
                null,
                "{}"
        );

        registry.terminate(intervention.interventionId());

        assertEquals(
                PendingAgentTaskRegistry.ClaimStatus.MISSING,
                registry.claim(intervention.interventionId()).status()
        );
    }

    @Test
    void expiredTaskIsReportedAsExpiredAndCannotBeClaimed() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-14T10:00:00Z"));
        PendingAgentTaskRegistry registry = new PendingAgentTaskRegistry(
                clock, Duration.ofMinutes(30), 10
        );
        GovernanceIntervention intervention = pendingIntervention(UUID.randomUUID());
        registry.register(
                intervention,
                new AgentCoordinatorRequest("Send this email", "customer@example.test", null),
                AgentCoordinator.IntentAction.EMAIL_REQUEST,
                "EMAIL",
                null,
                "{}"
        );

        clock.advance(Duration.ofMinutes(31));

        assertEquals(
                PendingAgentTaskRegistry.ClaimStatus.EXPIRED,
                registry.claim(intervention.interventionId()).status()
        );
    }

    @Test
    void rejectsArgumentsThatDoNotMatchTheApprovedDigest() {
        UUID conversationId = UUID.randomUUID();
        GovernanceIntervention intervention = pendingIntervention(conversationId);
        PendingAgentTaskRegistry registry = new PendingAgentTaskRegistry();

        assertThrows(IllegalArgumentException.class, () -> registry.register(
                intervention,
                new AgentCoordinatorRequest("Send this email", "customer@example.test", null),
                AgentCoordinator.IntentAction.EMAIL_REQUEST,
                "EMAIL",
                null,
                "{\"recipient\":\"different@example.test\"}"
        ));

        assertEquals(
                PendingAgentTaskRegistry.ClaimStatus.MISSING,
                registry.claim(intervention.interventionId()).status()
        );
    }

    private GovernanceIntervention pendingIntervention(UUID conversationId) {
        return new GovernanceIntervention(
                "intervention-registry-1",
                "workflow-registry-1",
                "call-registry-1",
                "EmailAgent",
                WorkflowType.EMAIL_COMMUNICATION,
                WorkflowCapability.SEND_EMAIL,
                RiskLevel.HIGH,
                com.agentops.guardian.governance.risk.GovernanceIntervention.REQUIRE_APPROVAL,
                GovernanceInterventionStatus.PENDING,
                "Approval required.",
                Instant.now(),
                null,
                null,
                null,
                "EMAIL",
                WorkflowState.REPORT_GENERATED,
                DataClassification.ANALYTICAL,
                conversationId,
                "sendEmail",
                com.agentops.guardian.governance.execution.ApprovedActionGrant.argumentsDigest("{}")
        );
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
