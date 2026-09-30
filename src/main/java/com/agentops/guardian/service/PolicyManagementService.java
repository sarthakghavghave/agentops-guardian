package com.agentops.guardian.service;

import com.agentops.guardian.governance.policy.PolicyDefinition;
import com.agentops.guardian.governance.policy.PolicyDefinitionEntity;
import com.agentops.guardian.governance.policy.PolicyDefinitionRepository;
import com.agentops.guardian.governance.policy.dto.PolicyDefinitionRequest;
import com.agentops.guardian.governance.policy.dto.PolicyDefinitionResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class PolicyManagementService {

    private final PolicyDefinitionRepository repository;

    public PolicyManagementService(PolicyDefinitionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PolicyDefinitionResponse> getAll() {
        return repository.findAllByOrderByPriorityAscPolicyIdAsc().stream()
                .map(PolicyDefinitionEntity::toDomain)
                .map(PolicyDefinitionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PolicyDefinitionResponse getByPolicyId(String policyId) {
        return repository.findByPolicyId(policyId)
                .map(PolicyDefinitionEntity::toDomain)
                .map(PolicyDefinitionResponse::from)
                .orElseThrow(() -> notFound(policyId));
    }

    @Transactional
    public PolicyDefinitionResponse create(PolicyDefinitionRequest request) {
        PolicyDefinition definition = request.toDomain();
        if (repository.findByPolicyId(definition.policyId()).isPresent()) {
            throw conflict(definition.policyId());
        }

        try {
            return PolicyDefinitionResponse.from(
                    repository.saveAndFlush(PolicyDefinitionEntity.from(definition)).toDomain()
            );
        } catch (DataIntegrityViolationException exception) {
            throw conflict(definition.policyId());
        }
    }

    @Transactional
    public PolicyDefinitionResponse update(String policyId, PolicyDefinitionRequest request) {
        PolicyDefinitionEntity entity = repository.findByPolicyId(policyId)
            .orElseThrow(() -> notFound(policyId));

        if (!policyId.equals(request.policyId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The policy id in the request must match the path policy id."
            );
        }

        PolicyDefinition definition = request.toDomain();
        entity.updateFrom(definition);
        return PolicyDefinitionResponse.from(repository.saveAndFlush(entity).toDomain());
    }

    private ResponseStatusException conflict(String policyId) {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Policy already exists: " + policyId);
    }

    private ResponseStatusException notFound(String policyId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy not found: " + policyId);
    }
}