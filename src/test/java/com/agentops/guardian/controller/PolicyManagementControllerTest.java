package com.agentops.guardian.controller;

import com.agentops.guardian.governance.policy.PolicyAction;
import com.agentops.guardian.governance.policy.PolicyConditionField;
import com.agentops.guardian.governance.policy.PolicyConditionOperator;
import com.agentops.guardian.governance.policy.dto.PolicyDefinitionRequest;
import com.agentops.guardian.governance.policy.dto.PolicyDefinitionResponse;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.service.PolicyManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PolicyManagementController.class)
class PolicyManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicyManagementService policyService;

    @Test
    void createsPolicy() throws Exception {
        when(policyService.create(any(PolicyDefinitionRequest.class))).thenReturn(response("policy-1"));

        mockMvc.perform(post("/api/policies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("policy-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.policyId").value("policy-1"));
    }

    @Test
    void updatesPolicy() throws Exception {
        when(policyService.update(eq("policy-1"), any(PolicyDefinitionRequest.class)))
                .thenReturn(response("policy-1"));

        mockMvc.perform(put("/api/policies/policy-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("policy-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void getsPoliciesAndSinglePolicy() throws Exception {
        when(policyService.getAll()).thenReturn(List.of(response("policy-1")));
        when(policyService.getByPolicyId("policy-1")).thenReturn(response("policy-1"));

        mockMvc.perform(get("/api/policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].policyId").value("policy-1"));
        mockMvc.perform(get("/api/policies/policy-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyId").value("policy-1"));
    }

    @Test
    void mapsUnknownPolicyToNotFound() throws Exception {
        when(policyService.getByPolicyId("missing"))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND
                ));

        mockMvc.perform(get("/api/policies/missing"))
                .andExpect(status().isNotFound());
    }

    private PolicyDefinitionResponse response(String policyId) {
        return new PolicyDefinitionResponse(
                policyId,
                "Policy name",
                "Policy description",
                true,
                10,
                "getCustomerData",
                WorkflowCapability.READ_CUSTOMER_DATA,
                PolicyConditionField.DATA_CLASSIFICATION,
                PolicyConditionOperator.EQUALS,
                "RAW_CUSTOMER_DATA",
                PolicyAction.BLOCK
        );
    }

    private String requestJson(String policyId) {
        return """
                {
                  "policyId": "%s",
                  "name": "Policy name",
                  "description": "Policy description",
                  "enabled": true,
                  "priority": 10,
                  "targetToolName": "getCustomerData",
                  "targetCapability": "READ_CUSTOMER_DATA",
                  "conditionField": "DATA_CLASSIFICATION",
                  "conditionOperator": "EQUALS",
                  "conditionValue": "RAW_CUSTOMER_DATA",
                  "action": "BLOCK"
                }
                """.formatted(policyId);
    }
}