package com.agentops.guardian.governance.policy;

import com.agentops.guardian.governance.workflow.WorkflowCapability;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ConversationHistoryAccessPolicyInitializer implements CommandLineRunner {

    static final String POLICY_ID = "BLOCK_EMAIL_HISTORY_AFTER_CUSTOMER_READ";
    static final String BLOCK_REASON =
            "EmailAgent cannot access shared conversation history because customer data was previously retrieved in this conversation.";

    private final PolicyDefinitionRepository repository;

    public ConversationHistoryAccessPolicyInitializer(PolicyDefinitionRepository repository) {
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
                "Block email access to history after customer read",
                BLOCK_REASON,
                true,
                1,
                new PolicyTarget("readConversationHistory", WorkflowCapability.READ_CONVERSATION_HISTORY),
                PolicyConditionField.VERIFIED_CUSTOMER_READ_IN_CONVERSATION,
                PolicyConditionOperator.EQUALS,
                "true",
                PolicyAction.BLOCK
        );
    }
}
