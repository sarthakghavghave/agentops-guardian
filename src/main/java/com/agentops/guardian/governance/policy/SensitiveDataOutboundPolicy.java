package com.agentops.guardian.governance.policy;

import com.agentops.guardian.governance.model.GovernanceDecision;
import com.agentops.guardian.governance.model.ToolCallEvent;
import com.agentops.guardian.governance.model.WorkflowContext;
import org.springframework.stereotype.Component;

@Component
public class SensitiveDataOutboundPolicy implements GovernancePolicy {
    private static final String CUSTOMER_DATA_TOOL = "getCustomerData";
    private static final String REPORT_TOOL = "generateReport";
    private static final String EMAIL_TOOL = "sendEmail";
    private static final String ANALYTICAL_REPORT = "ANALYTICAL";
    private static final String REDACTED_DETAIL_REPORT = "REDACTED_DETAIL";

    @Override
    public GovernanceDecision evaluate(WorkflowContext workflow, ToolCallEvent proposedCall) {
        if (!EMAIL_TOOL.equals(proposedCall.toolName())) {
            return allow("Tool call does not involve outbound email.");
        }

        boolean customerDataAcquired = workflow.getToolCalls().stream()
                .anyMatch(call -> CUSTOMER_DATA_TOOL.equals(call.toolName()));

        if (!customerDataAcquired) {
            return allow("No customer data was acquired in this workflow.");
        }

        boolean approvedTransformationPerformed = workflow.getToolCalls().stream()
                .anyMatch(this::isApprovedTransformation);

        if (!approvedTransformationPerformed) {
            return new GovernanceDecision(
                    GovernanceDecision.DecisionType.BLOCK,
                    "Outbound email blocked because customer data was acquired without an approved data transformation."
            );
        }

        return allow("Customer data was transformed using an approved report type before outbound email.");
    }

    private boolean isApprovedTransformation(ToolCallEvent call) {
        if (!REPORT_TOOL.equals(call.toolName())) {
            return false;
        }

        String arguments = call.arguments();

        if (arguments == null || arguments.isBlank()) {
            return false;
        }

        return arguments.contains("\"reportType\":\"" + ANALYTICAL_REPORT + "\"")
                || arguments.contains("\"reportType\":\"" + REDACTED_DETAIL_REPORT + "\"");
    }

    private GovernanceDecision allow(String reason) {
        return new GovernanceDecision(
                GovernanceDecision.DecisionType.ALLOW,
                reason
        );
    }
}