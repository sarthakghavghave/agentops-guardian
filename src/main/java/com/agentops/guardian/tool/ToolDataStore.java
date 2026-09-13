package com.agentops.guardian.tool;

import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.governance.context.WorkflowContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ToolDataStore {

    private static final String CUSTOMER_DATA_KEY = ToolDataStore.class.getName() + ".customerData";
    private final WorkflowContextManager workflowContextManager;

    public void storeCustomerData(CustomerDataTools.CustomerDataSet data) {
        currentWorkflow().putData(CUSTOMER_DATA_KEY, data);
    }

    public CustomerDataTools.CustomerDataSet getCustomerData() {

        Object data = currentWorkflow().getData(CUSTOMER_DATA_KEY);

        if (!(data instanceof CustomerDataTools.CustomerDataSet customerData)) {
            throw new IllegalStateException("No customer data is available in the current workflow.");
        }

        return customerData;
    }

    public void clear() {
        currentWorkflow().removeData(CUSTOMER_DATA_KEY);
    }

    private WorkflowContext currentWorkflow() {
        WorkflowContext context = workflowContextManager.current();

        if (context == null) {
            throw new IllegalStateException("No active workflow.");
        }

        return context;
    }
}