package com.agentops.guardian.governance.risk;

import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.context.WorkflowState;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.model.DataTransformation;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RiskEvaluator {

    public RiskAssessment assess(WorkflowContext context, WorkflowCapability capability) {
        if (context == null) {
            throw new IllegalArgumentException("Workflow context is required.");
        }
        if (capability == null) {
            throw new IllegalArgumentException("Workflow capability is required.");
        }

        DataClassification classification = context.getCurrentDataClassification();
        boolean externalAction = isExternalAction(capability);
        boolean meaningfulPriorActivity = hasMeaningfulPriorActivity(context, classification);
        List<RiskFactor> factors = new ArrayList<>();

        if (externalAction) {
            factors.add(new RiskFactor(
                    RiskFactorType.HIGH_CONSEQUENCE_CAPABILITY,
                    highConsequenceReason(capability)
            ));
            factors.add(new RiskFactor(
                    RiskFactorType.EXTERNAL_SIDE_EFFECT,
                    externalSideEffectReason(capability)
            ));
        }

        if (classification == DataClassification.RAW_CUSTOMER_DATA) {
            factors.add(new RiskFactor(
                    RiskFactorType.SENSITIVE_DATA_CONTEXT,
                    "The workflow currently contains raw customer data."
            ));
        }

        if (externalAction && meaningfulPriorActivity) {
            factors.add(new RiskFactor(
                    RiskFactorType.TRAJECTORY_ESCALATION,
                    trajectoryReason(context, classification)
            ));
        }

        RiskLevel level = determineLevel(capability, classification);
        String reason = assessmentReason(
                level,
                capability,
                classification,
                context,
                meaningfulPriorActivity
        );
        return new RiskAssessment(level, factors, reason);
    }

    private boolean isExternalAction(WorkflowCapability capability) {
        return switch (capability) {
            case SEND_EMAIL, RETURN_ORDER -> true;
            case GENERATE_REPORT, READ_CUSTOMER_DATA, READ_ORDER, READ_PRODUCT -> false;
        };
    }

    private boolean hasMeaningfulPriorActivity(
            WorkflowContext context,
            DataClassification classification
    ) {
        return classification != null
                || context.getLastTransformation() != null
                || !WorkflowContext.START_NODE_ID.equals(context.getCurrentNodeId())
                || context.getCurrentState() != WorkflowState.STARTED;
    }

    private RiskLevel determineLevel(
            WorkflowCapability capability,
            DataClassification classification
    ) {
        if (isExternalAction(capability)) {
            return classification == DataClassification.RAW_CUSTOMER_DATA
                    ? RiskLevel.CRITICAL
                    : RiskLevel.HIGH;
        }
        if (classification == DataClassification.RAW_CUSTOMER_DATA) {
            return RiskLevel.MODERATE;
        }
        if (capability == WorkflowCapability.GENERATE_REPORT && classification == null) {
            return RiskLevel.MODERATE;
        }
        return RiskLevel.LOW;
    }

    private String highConsequenceReason(WorkflowCapability capability) {
        return switch (capability) {
            case SEND_EMAIL -> "Sending email can communicate information outside the workflow.";
            case RETURN_ORDER -> "Returning an order can change its status outside the workflow.";
            default -> throw new IllegalArgumentException("Capability is not externally consequential: " + capability);
        };
    }

    private String externalSideEffectReason(WorkflowCapability capability) {
        return switch (capability) {
            case SEND_EMAIL -> "The email may be received by an external recipient.";
            case RETURN_ORDER -> "The return action may trigger an external order or fulfillment process.";
            default -> throw new IllegalArgumentException("Capability has no external side effect: " + capability);
        };
    }

    private String trajectoryReason(WorkflowContext context, DataClassification classification) {
        DataTransformation transformation = context.getLastTransformation();
        if (transformation != null) {
            return "The workflow already performed " + transformation.transformationType()
                    + " and now contains " + transformation.resultClassification() + " data.";
        }
        if (classification != null) {
            return "The workflow already contains data classified as " + classification + ".";
        }
        return "The workflow already advanced to node " + context.getCurrentNodeId()
                + " in state " + context.getCurrentState() + ".";
    }

    private String assessmentReason(
            RiskLevel level,
            WorkflowCapability capability,
            DataClassification classification,
            WorkflowContext context,
            boolean meaningfulPriorActivity
    ) {
        if (level == RiskLevel.CRITICAL) {
            return capability == WorkflowCapability.SEND_EMAIL
                    ? "External communication is being attempted while the workflow still contains raw customer data."
                    : "An external order action is being attempted while the workflow still contains raw customer data.";
        }
        if (level == RiskLevel.HIGH && meaningfulPriorActivity) {
            String reason = capability == WorkflowCapability.SEND_EMAIL
                    ? "External communication is consequential because " + trajectorySummary(context, classification) + "."
                    : "The order action is consequential because " + trajectorySummary(context, classification) + ".";
            if (classification == null) {
                return reason + " Data classification is unavailable, so no sensitivity is assumed.";
            }
            return reason;
        }
        if (level == RiskLevel.HIGH) {
            return capability == WorkflowCapability.SEND_EMAIL
                    ? "Sending email communicates information beyond the workflow boundary. Data classification is unavailable, so no sensitivity is assumed."
                    : "Returning an order may trigger an external order or fulfillment process. Data classification is unavailable, so no sensitivity is assumed.";
        }
        if (classification == DataClassification.RAW_CUSTOMER_DATA) {
            return "This action is internal, but the workflow currently contains raw customer data.";
        }
        if (classification == null) {
            if (capability == WorkflowCapability.GENERATE_REPORT) {
                return "Report generation has no verified source data classification, so the available context is insufficient to assess the transformation fully.";
            }
            return "This capability has no inherent external side effect; data classification is unavailable, so no sensitivity is assumed.";
        }
        return "This capability has no inherent external side effect, and the current data classification is "
                + classification + ".";
    }

    private String trajectorySummary(WorkflowContext context, DataClassification classification) {
        DataTransformation transformation = context.getLastTransformation();
        if (transformation != null) {
            return "the workflow already performed " + transformation.transformationType()
                    + " and produced " + transformation.resultClassification() + " data";
        }
        if (classification != null) {
            return "the workflow already contains " + classification + " data";
        }
        return "the workflow already advanced to " + context.getCurrentNodeId()
                + " in state " + context.getCurrentState();
    }
}