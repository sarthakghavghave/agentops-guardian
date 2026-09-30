package com.agentops.guardian.service;

import com.agentops.guardian.governance.policy.PolicyAction;
import com.agentops.guardian.governance.policy.PolicyConditionField;
import com.agentops.guardian.governance.policy.PolicyConditionOperator;
import com.agentops.guardian.governance.policy.PolicyDefinition;
import com.agentops.guardian.governance.policy.PolicyDefinitionEntity;
import com.agentops.guardian.governance.policy.PolicyDefinitionRepository;
import com.agentops.guardian.governance.policy.dto.PolicyDefinitionRequest;
import com.agentops.guardian.governance.policy.dto.PolicyDefinitionResponse;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PolicyManagementServiceTest {

    private PolicyDefinitionRepository repository;
    private PolicyManagementService service;

    @BeforeEach
    void setUp() {
        repository = mock(PolicyDefinitionRepository.class);
        service = new PolicyManagementService(repository);
        when(repository.saveAndFlush(any(PolicyDefinitionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsAndReadsPolicy() {
        PolicyDefinitionRequest request = request("policy-1", true, "Initial policy");
        when(repository.findByPolicyId("policy-1")).thenReturn(Optional.empty());

        PolicyDefinitionResponse created = service.create(request);
        when(repository.findAllByOrderByPriorityAscPolicyIdAsc())
                .thenReturn(List.of(PolicyDefinitionEntity.from(request.toDomain())));
        List<PolicyDefinitionResponse> listed = service.getAll();
        when(repository.findByPolicyId("policy-1"))
                .thenReturn(Optional.of(PolicyDefinitionEntity.from(request.toDomain())));
        PolicyDefinitionResponse found = service.getByPolicyId("policy-1");

        assertEquals("policy-1", created.policyId());
        assertEquals("Initial policy", created.description());
        assertEquals(1, listed.size());
        assertEquals("policy-1", found.policyId());
    }

    @Test
    void rejectsDuplicatePolicyId() {
        PolicyDefinition existing = request("policy-1", true, "Existing").toDomain();
        when(repository.findByPolicyId("policy-1"))
                .thenReturn(Optional.of(PolicyDefinitionEntity.from(existing)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.create(request("policy-1", true, "Duplicate"))
        );

        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void updatesExistingPolicy() {
        PolicyDefinitionEntity entity = PolicyDefinitionEntity.from(
                request("policy-1", true, "Before update").toDomain()
        );
        when(repository.findByPolicyId("policy-1")).thenReturn(Optional.of(entity));

        PolicyDefinitionResponse updated = service.update(
                "policy-1",
                request("policy-1", false, "After update")
        );

        assertEquals("After update", updated.description());
        assertEquals(false, updated.enabled());
        verify(repository).saveAndFlush(entity);
    }

    @Test
    void returnsNotFoundForUnknownPolicy() {
        when(repository.findByPolicyId("missing")).thenReturn(Optional.empty());

        ResponseStatusException getException = assertThrows(
                ResponseStatusException.class,
                () -> service.getByPolicyId("missing")
        );
        ResponseStatusException updateException = assertThrows(
                ResponseStatusException.class,
                () -> service.update("missing", request("missing", true, "Missing"))
        );

        assertEquals(404, getException.getStatusCode().value());
        assertEquals(404, updateException.getStatusCode().value());
    }

    private PolicyDefinitionRequest request(String policyId, boolean enabled, String description) {
        return new PolicyDefinitionRequest(
                policyId,
                "Policy name",
                description,
                enabled,
                10,
                "getCustomerData",
                WorkflowCapability.READ_CUSTOMER_DATA,
                PolicyConditionField.DATA_CLASSIFICATION,
                PolicyConditionOperator.EQUALS,
                "RAW_CUSTOMER_DATA",
                PolicyAction.BLOCK
        );
    }
}