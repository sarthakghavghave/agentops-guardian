package com.agentops.guardian.governance.audit;

public interface AuditService {
    void record(AuditEvent event);
}
