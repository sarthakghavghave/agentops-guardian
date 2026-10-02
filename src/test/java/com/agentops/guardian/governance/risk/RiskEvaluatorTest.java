package com.agentops.guardian.governance.risk;

import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.model.DataTransformation;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowDefinition;
import com.agentops.guardian.governance.workflow.WorkflowGraph;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskEvaluatorTest {

    private final RiskEvaluator evaluator = new RiskEvaluator();

    @Test
    void customerDataReadWithoutExistingClassificationIsLowAndExplained() {
        RiskAssessment assessment = evaluator.assess(workflow(), WorkflowCapability.READ_CUSTOMER_DATA);

        assertEquals(RiskLevel.LOW, assessment.level());
        assertTrue(assessment.reason().contains("classification is unavailable"));
        assertTrue(assessment.factors().isEmpty());
    }

    @Test
    void analyticalReportHasNoFalseSensitiveDataFactor() {
        WorkflowContext context = workflow();
        context.markDataAcquired(DataClassification.ANALYTICAL);

        RiskAssessment assessment = evaluator.assess(context, WorkflowCapability.GENERATE_REPORT);

        assertEquals(RiskLevel.LOW, assessment.level());
        assertFalse(hasFactor(assessment, RiskFactorType.SENSITIVE_DATA_CONTEXT));
        assertTrue(assessment.reason().contains("ANALYTICAL"));
    }

    @Test
    void emailAfterAnalyticalTransformationIsHighAndExplainsExternalConsequence() {
        WorkflowContext context = transformedWorkflow(DataClassification.ANALYTICAL);

        RiskAssessment assessment = evaluator.assess(context, WorkflowCapability.SEND_EMAIL);

        assertEquals(RiskLevel.HIGH, assessment.level());
        assertTrue(hasFactor(assessment, RiskFactorType.EXTERNAL_SIDE_EFFECT));
        assertTrue(hasFactor(assessment, RiskFactorType.HIGH_CONSEQUENCE_CAPABILITY));
        assertTrue(hasFactor(assessment, RiskFactorType.TRAJECTORY_ESCALATION));
        assertTrue(assessment.reason().contains("External communication"));
        assertTrue(assessment.reason().contains("ANALYTICAL"));
        assertFalse(hasFactor(assessment, RiskFactorType.SENSITIVE_DATA_CONTEXT));
    }

    @Test
    void emailAfterRedactedTransformationIsHighWithoutClaimingRawData() {
        WorkflowContext context = transformedWorkflow(DataClassification.REDACTED_DETAIL);

        RiskAssessment assessment = evaluator.assess(context, WorkflowCapability.SEND_EMAIL);

        assertEquals(RiskLevel.HIGH, assessment.level());
        assertTrue(hasFactor(assessment, RiskFactorType.EXTERNAL_SIDE_EFFECT));
        assertFalse(hasFactor(assessment, RiskFactorType.SENSITIVE_DATA_CONTEXT));
        assertFalse(assessment.reason().contains("raw customer data"));
        assertTrue(assessment.reason().contains("REDACTED_DETAIL"));
    }

    @Test
    void emailWithRawCustomerDataIsCriticalAndExplainsSensitivity() {
        WorkflowContext context = workflow();
        context.markDataAcquired(DataClassification.RAW_CUSTOMER_DATA);

        RiskAssessment assessment = evaluator.assess(context, WorkflowCapability.SEND_EMAIL);

        assertEquals(RiskLevel.CRITICAL, assessment.level());
        assertTrue(hasFactor(assessment, RiskFactorType.SENSITIVE_DATA_CONTEXT));
        assertTrue(hasFactor(assessment, RiskFactorType.EXTERNAL_SIDE_EFFECT));
        assertTrue(assessment.reason().contains("raw customer data"));
    }

    @Test
    void trajectoryEscalationRequiresActualPriorWorkflowProgress() {
        RiskAssessment initial = evaluator.assess(workflow(), WorkflowCapability.SEND_EMAIL);
        WorkflowContext progressed = workflow();
        progressed.advanceAfterCapability(WorkflowCapability.READ_CUSTOMER_DATA);
        RiskAssessment afterAcquisition = evaluator.assess(progressed, WorkflowCapability.SEND_EMAIL);

        assertFalse(hasFactor(initial, RiskFactorType.TRAJECTORY_ESCALATION));
        assertTrue(initial.reason().contains("Data classification is unavailable"));
        assertTrue(hasFactor(afterAcquisition, RiskFactorType.TRAJECTORY_ESCALATION));
        assertTrue(afterAcquisition.factors().stream()
                .filter(factor -> factor.type() == RiskFactorType.TRAJECTORY_ESCALATION)
                .anyMatch(factor -> factor.reason().contains("CUSTOMER_DATA")
                        && factor.reason().contains("DATA_ACQUIRED")));
    }

    @Test
    void assessmentDefensivelyCopiesAndProtectsFactors() {
        List<RiskFactor> factors = new ArrayList<>();
        factors.add(new RiskFactor(RiskFactorType.EXTERNAL_SIDE_EFFECT, "External effect."));
        RiskAssessment assessment = new RiskAssessment(RiskLevel.HIGH, factors, "Explained.");
        factors.add(new RiskFactor(RiskFactorType.TRAJECTORY_ESCALATION, "Later addition."));

        assertEquals(1, assessment.factors().size());
        assertThrows(UnsupportedOperationException.class,
                () -> assessment.factors().add(new RiskFactor(RiskFactorType.SENSITIVE_DATA_CONTEXT, "Sensitive.")));
    }

    @Test
    void rejectsMissingInputsAndUnexplainedDomainValues() {
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.assess(null, WorkflowCapability.SEND_EMAIL));
        assertThrows(IllegalArgumentException.class,
                () -> evaluator.assess(workflow(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new RiskFactor(null, "Reason."));
        assertThrows(IllegalArgumentException.class,
                () -> new RiskFactor(RiskFactorType.EXTERNAL_SIDE_EFFECT, " "));
        assertThrows(IllegalArgumentException.class,
                () -> new RiskAssessment(null, List.of(), "Reason."));
        assertThrows(IllegalArgumentException.class,
                () -> new RiskAssessment(RiskLevel.LOW, null, "Reason."));
        assertThrows(IllegalArgumentException.class,
                () -> new RiskAssessment(RiskLevel.LOW, List.of(), " "));
    }

    @Test
    void reportWithUnavailableClassificationIsModerateWithoutInventingSensitivity() {
        RiskAssessment assessment = evaluator.assess(workflow(), WorkflowCapability.GENERATE_REPORT);

        assertEquals(RiskLevel.MODERATE, assessment.level());
        assertFalse(hasFactor(assessment, RiskFactorType.SENSITIVE_DATA_CONTEXT));
        assertTrue(assessment.reason().contains("no verified source data classification"));
    }

    private WorkflowContext transformedWorkflow(DataClassification resultClassification) {
        WorkflowContext context = workflow();
        context.recordTransformation(new DataTransformation(
                DataClassification.RAW_CUSTOMER_DATA,
                resultClassification,
                resultClassification.name(),
                Instant.parse("2026-10-02T10:00:00Z")
        ));
        return context;
    }

    private WorkflowContext workflow() {
        WorkflowDefinition definition = new WorkflowDefinition(
                WorkflowType.CUSTOMER_REPORTING,
                EnumSet.allOf(WorkflowCapability.class),
                WorkflowGraph.customerReportingGraph()
        );
        return new WorkflowContext("RiskEvaluatorTestAgent", WorkflowType.CUSTOMER_REPORTING, "Test risk", definition);
    }

    private boolean hasFactor(RiskAssessment assessment, RiskFactorType type) {
        return assessment.factors().stream().anyMatch(factor -> factor.type() == type);
    }
}