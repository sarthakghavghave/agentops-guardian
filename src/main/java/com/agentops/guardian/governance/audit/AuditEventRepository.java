package com.agentops.guardian.governance.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEventEntity, Long> {
    List<AuditEventEntity> findByWorkflowIdOrderByTimestampAsc(String workflowId);
}
