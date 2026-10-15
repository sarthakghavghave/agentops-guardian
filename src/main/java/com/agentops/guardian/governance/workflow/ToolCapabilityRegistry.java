package com.agentops.guardian.governance.workflow;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ToolCapabilityRegistry {

    private final Map<String, WorkflowCapability> capabilities = Map.of(
            "getCustomerData", WorkflowCapability.READ_CUSTOMER_DATA,
            "getCustomer", WorkflowCapability.READ_CUSTOMER_DATA,
            "searchCustomersByName", WorkflowCapability.READ_CUSTOMER_DATA,
            "readConversationHistory", WorkflowCapability.READ_CONVERSATION_HISTORY,
            "generateReport", WorkflowCapability.GENERATE_REPORT,
            "sendEmail", WorkflowCapability.SEND_EMAIL,
            "returnOrder", WorkflowCapability.RETURN_ORDER
    );

    public WorkflowCapability getCapability(String toolName) {
        return capabilities.get(toolName);
    }
}