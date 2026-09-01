package com.agentops.guardian.tool;

import com.agentops.guardian.governance.WorkflowContextHolder;
import com.agentops.guardian.governance.model.WorkflowContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ToolDataStore {

    private static final String CUSTOMER_DATA_KEY = ToolDataStore.class.getName() + ".customerData";
    private final WorkflowContextHolder workflowContextHolder;

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
        WorkflowContext context = workflowContextHolder.getCurrentWorkflow();

        if (context == null) {
            throw new IllegalStateException("No active workflow.");
        }

        return context;
    }
}