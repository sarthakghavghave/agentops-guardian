package com.agentops.guardian.governance.audit;

public class NoOpAuditService implements AuditService {
    @Override
    public void record(AuditEvent event) {
        // Intentionally no-op. Persistence will be added later.
    }
}
