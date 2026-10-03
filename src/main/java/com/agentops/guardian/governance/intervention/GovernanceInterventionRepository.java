package com.agentops.guardian.governance.intervention;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GovernanceInterventionRepository extends JpaRepository<GovernanceInterventionEntity, String> {
    Optional<GovernanceInterventionEntity> findByInterventionId(String interventionId);

    List<GovernanceInterventionEntity> findAllByOrderByCreatedAtDescInterventionIdAsc();
}