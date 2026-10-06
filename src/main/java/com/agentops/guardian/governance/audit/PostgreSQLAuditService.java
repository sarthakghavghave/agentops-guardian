package com.agentops.guardian.governance.audit;

import com.agentops.guardian.governance.audit.live.GovernanceAuditEventPersisted;
import com.agentops.guardian.governance.audit.live.GovernanceEventStreamPayload;
import com.agentops.guardian.governance.audit.dto.WorkflowTrajectoryEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class PostgreSQLAuditService implements AuditService {

	private final AuditEventRepository repository;
	private final ApplicationEventPublisher eventPublisher;

	public PostgreSQLAuditService(
			AuditEventRepository repository,
			ApplicationEventPublisher eventPublisher
	) {
		this.repository = repository;
		this.eventPublisher = eventPublisher;
	}

	@Override
	public void record(AuditEvent event) {
		AuditEventEntity persisted = repository.save(
				AuditEventEntity.from(Objects.requireNonNull(event, "Audit event is required."))
		);
		if (persisted.getId() == null) {
			throw new IllegalStateException("Persisted audit event did not receive an identifier.");
		}

		eventPublisher.publishEvent(new GovernanceAuditEventPersisted(
				GovernanceEventStreamPayload.from(persisted, WorkflowTrajectoryEvent.from(persisted))
		));
	}
}
