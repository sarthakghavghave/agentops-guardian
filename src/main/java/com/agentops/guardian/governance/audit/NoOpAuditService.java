package com.agentops.guardian.governance.audit;

import org.springframework.stereotype.Component;

public class NoOpAuditService implements AuditService {
    @Override
    public void record(AuditEvent event) {
        // Intentionally no-op. Persistence will be added later.
    }
}
