package com.agentops.guardian.governance.policy;

import com.agentops.guardian.governance.model.DataClassification;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SensitiveDataOutboundPolicyInitializer implements CommandLineRunner {

    static final String POLICY_ID = "SENSITIVE_DATA_OUTBOUND";
    static final String BLOCK_REASON =
            "Outbound email blocked because the workflow still contains raw customer data.";

    private final PolicyDefinitionRepository repository;

    public SensitiveDataOutboundPolicyInitializer(PolicyDefinitionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.findByPolicyId(POLICY_ID).isEmpty()) {
            repository.save(PolicyDefinitionEntity.from(initialPolicy()));
        }
    }

    static PolicyDefinition initialPolicy() {
        return new PolicyDefinition(
                POLICY_ID,
                "Sensitive data outbound protection",
                BLOCK_REASON,
                true,
                1,
                new PolicyTarget(null, WorkflowCapability.SEND_EMAIL),
                PolicyConditionField.DATA_CLASSIFICATION,
                PolicyConditionOperator.EQUALS,
                DataClassification.RAW_CUSTOMER_DATA.name(),
                PolicyAction.BLOCK
        );
    }
}