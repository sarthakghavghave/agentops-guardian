package com.agentops.guardian.controller;

import com.agentops.guardian.governance.policy.dto.PolicyDefinitionRequest;
import com.agentops.guardian.governance.policy.dto.PolicyDefinitionResponse;
import com.agentops.guardian.service.PolicyManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/policies")
public class PolicyManagementController {

    private final PolicyManagementService policyService;

    public PolicyManagementController(PolicyManagementService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public List<PolicyDefinitionResponse> getAll() {
        return policyService.getAll();
    }

    @GetMapping("/{policyId}")
    public PolicyDefinitionResponse getByPolicyId(@PathVariable String policyId) {
        return policyService.getByPolicyId(policyId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyDefinitionResponse create(@Valid @RequestBody PolicyDefinitionRequest request) {
        return policyService.create(request);
    }

    @PutMapping("/{policyId}")
    public PolicyDefinitionResponse update(
            @PathVariable String policyId,
            @Valid @RequestBody PolicyDefinitionRequest request
    ) {
        return policyService.update(policyId, request);
    }
}