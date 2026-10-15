package com.agentops.guardian.governance.execution;

import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.intervention.GovernanceIntervention;
import com.agentops.guardian.governance.intervention.GovernanceInterventionStatus;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.risk.RiskLevel;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowDefinitionRegistry;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ApprovedActionGrantTest {

    @Test
    void grantIsBoundToConversationAgentCapabilityToolAndExactArgumentsAndCanBeUsedOnce() {
        UUID conversationId = UUID.randomUUID();
        GovernanceIntervention intervention = approvedIntervention(conversationId, "sendEmail", "{}");
        WorkflowContext workflow = emailWorkflow(conversationId);
        ApprovedActionGrant grant = new ApprovedActionGrant();

        grant.resume(intervention, () -> {
            assertFalse(grant.consume(workflow, WorkflowCapability.SEND_EMAIL, "sendEmail", "{\"to\":\"other\"}"));
            assertFalse(grant.consume(
                    emailWorkflow(UUID.randomUUID()), WorkflowCapability.SEND_EMAIL, "sendEmail", "{}"
            ));
            assertTrue(grant.consume(workflow, WorkflowCapability.SEND_EMAIL, "sendEmail", "{}"));
            assertFalse(grant.consume(workflow, WorkflowCapability.SEND_EMAIL, "sendEmail", "{}"));
            return null;
        });
    }

    @Test
    void grantDoesNotAuthorizeAnotherToolOrCapability() {
        UUID conversationId = UUID.randomUUID();
        GovernanceIntervention intervention = approvedIntervention(conversationId, "sendEmail", "{}");
        ApprovedActionGrant grant = new ApprovedActionGrant();

        grant.resume(intervention, () -> {
            assertFalse(grant.consume(emailWorkflow(conversationId), WorkflowCapability.GENERATE_REPORT, "sendEmail", "{}"));
            assertFalse(grant.consume(emailWorkflow(conversationId), WorkflowCapability.SEND_EMAIL, "otherTool", "{}"));
            return null;
        });
    }

    @Test
    void argumentDigestIgnoresJsonWhitespaceAndObjectKeyOrderButBindsValues() {
        String original = """
                {"recipient":"operator@example.test","subject":"Meeting","body":"Join us"}
                """;
        String equivalent = "{\"body\":\"Join us\", \"subject\":\"Meeting\",\"recipient\":\"operator@example.test\"}";
        String different = "{\"body\":\"Join us today\",\"subject\":\"Meeting\",\"recipient\":\"operator@example.test\"}";

        assertEquals(
                ApprovedActionGrant.argumentsDigest(original),
                ApprovedActionGrant.argumentsDigest(equivalent)
        );
        assertFalse(
                ApprovedActionGrant.argumentsDigest(original)
                        .equals(ApprovedActionGrant.argumentsDigest(different))
        );
    }

    @Test
    void grantConsumesEquivalentSerializedArgumentsAndStillRejectsChangedValues() {
        UUID conversationId = UUID.randomUUID();
        String approvedArguments = "{\"recipient\":\"operator@example.test\",\"subject\":\"Meeting\",\"body\":\"Join us\"}";
        GovernanceIntervention intervention = approvedIntervention(conversationId, "sendEmail", approvedArguments);
        WorkflowContext workflow = emailWorkflow(conversationId);
        ApprovedActionGrant grant = new ApprovedActionGrant();

        grant.resume(intervention, () -> {
            assertFalse(grant.consume(workflow, WorkflowCapability.SEND_EMAIL, "sendEmail",
                    "{\"recipient\":\"operator@example.test\",\"subject\":\"Meeting\",\"body\":\"Join us today\"}"));
            assertTrue(grant.consume(workflow, WorkflowCapability.SEND_EMAIL, "sendEmail",
                    "{\"body\":\"Join us\", \"subject\":\"Meeting\",\"recipient\":\"operator@example.test\"}"));
            assertFalse(grant.consume(workflow, WorkflowCapability.SEND_EMAIL, "sendEmail", approvedArguments));
            return null;
        });
    }

    private WorkflowContext emailWorkflow(UUID conversationId) {
        return new WorkflowContext(
                "EmailAgent",
                WorkflowType.EMAIL_COMMUNICATION,
                "Send a validated email",
                new WorkflowDefinitionRegistry().get(WorkflowType.EMAIL_COMMUNICATION),
                conversationId
        );
    }

    private GovernanceIntervention approvedIntervention(UUID conversationId, String toolName, String arguments) {
        return new GovernanceIntervention(
                "intervention-grant-1",
                "workflow-original-1",
                "send-call-original",
                "EmailAgent",
                WorkflowType.EMAIL_COMMUNICATION,
                WorkflowCapability.SEND_EMAIL,
                RiskLevel.HIGH,
                com.agentops.guardian.governance.risk.GovernanceIntervention.REQUIRE_APPROVAL,
                GovernanceInterventionStatus.APPROVED,
                "Approval required.",
                Instant.now(),
                Instant.now(),
                "reviewer",
                "Approved.",
                "EMAIL",
                WorkflowState.REPORT_GENERATED,
                DataClassification.ANALYTICAL,
                conversationId,
                toolName,
                ApprovedActionGrant.argumentsDigest(arguments)
        );
    }
}
