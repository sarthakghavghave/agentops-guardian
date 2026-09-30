package com.agentops.guardian.governance.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyDefinitionRepository extends JpaRepository<PolicyDefinitionEntity, Long> {
    Optional<PolicyDefinitionEntity> findByPolicyId(String policyId);

    List<PolicyDefinitionEntity> findAllByEnabledTrueOrderByPriorityAscPolicyIdAsc();

    List<PolicyDefinitionEntity> findAllByOrderByPriorityAscPolicyIdAsc();
}