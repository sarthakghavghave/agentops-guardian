package com.agentops.guardian.governance.audit;

import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class PostgreSQLAuditService implements AuditService {

	private final AuditEventRepository repository;

	public PostgreSQLAuditService(AuditEventRepository repository) {
		this.repository = repository;
	}

	@Override
	public void record(AuditEvent event) {
		repository.save(AuditEventEntity.from(Objects.requireNonNull(event, "Audit event is required.")));
	}
}
