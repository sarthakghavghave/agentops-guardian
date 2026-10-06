package com.agentops.guardian.governance.audit.live;

import com.agentops.guardian.governance.audit.AuditEventType;
import com.agentops.guardian.governance.audit.AuditEventEntity;
import com.agentops.guardian.governance.audit.AuditEventRepository;
import com.agentops.guardian.governance.model.GovernanceDecision.DecisionType;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.agentops.guardian.governance.workflow.WorkflowType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.beans.BeanUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LiveGovernanceEventPublisherTest {
    private final AuditEventRepository repository = mock(AuditEventRepository.class);
    private final LiveGovernanceEventPublisher publisher =
            new LiveGovernanceEventPublisher(repository, new SyncTaskExecutor());

    @AfterEach
    void stopPublisher() {
        publisher.stop();
    }

    @Test
    void replaysPersistedEventsAfterCursorInAscendingIdOrder() {
        AuditEventEntity ninth = entity(9L, "workflow-9");
        AuditEventEntity seventh = entity(7L, "workflow-7");
        when(repository.findAllByIdGreaterThanOrderByIdAsc(6L))
                .thenReturn(List.of(ninth, seventh));
        RecordingEmitter emitter = new RecordingEmitter();

        publisher.connect("6", emitter);

        assertEquals(List.of(7L, 9L), eventIds(emitter));
        verify(repository).findAllByIdGreaterThanOrderByIdAsc(6L);
    }

    @Test
    void deliversCommittedEventToEveryConnectedClientAndExcludesRuntimePayload() throws Exception {
        RecordingEmitter first = new RecordingEmitter();
        RecordingEmitter second = new RecordingEmitter();
        publisher.connect(null, first);
        publisher.connect(null, second);
        GovernanceEventStreamPayload payload = payload(entity(12L, "workflow-12"));

        publisher.onAuditEventCommitted(new GovernanceAuditEventPersisted(payload));

        assertEquals(List.of(12L), eventIds(first));
        assertEquals(List.of(12L), eventIds(second));
        String json = JsonMapper.builder().findAndAddModules().build().writeValueAsString(payload);
        assertTrue(json.contains("\"policyId\":\"TEST_POLICY\""));
        assertTrue(json.contains("\"policyDecision\":\"BLOCK\""));
        assertFalse(json.contains("toolArguments"));
        assertFalse(json.contains("customerPayload"));
    }

    @Test
    void failedClientDoesNotPreventDeliveryToOtherClients() {
        RecordingEmitter failing = new RecordingEmitter();
        failing.failSends = true;
        RecordingEmitter healthy = new RecordingEmitter();
        publisher.connect(null, failing);
        publisher.connect(null, healthy);

        publisher.onAuditEventCommitted(new GovernanceAuditEventPersisted(payload(entity(15L, "workflow-15"))));

        assertEquals(List.of(15L), eventIds(healthy));
        assertTrue(failing.completedWithError);
    }

    @Test
    void invalidCursorStartsFreshAndNoEventConnectionRemainsOpen() {
        RecordingEmitter emitter = new RecordingEmitter(0L);

        SseEmitter connected = publisher.connect("not-a-number", emitter);

        assertEquals(0L, connected.getTimeout());
        assertTrue(emitter.frames.isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void heartbeatIsTransportOnlyAndDoesNotQueryOrPersistAuditEvents() {
        RecordingEmitter emitter = new RecordingEmitter();
        publisher.connect(null, emitter);

        publisher.sendHeartbeats();

        assertFalse(emitter.frames.isEmpty());
        assertTrue(emitter.frames.stream()
                .flatMap(List::stream)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .anyMatch(value -> value.contains("heartbeat")));
        assertTrue(emitter.frames.stream()
                .flatMap(List::stream)
                .noneMatch(GovernanceEventStreamPayload.class::isInstance));
        verifyNoInteractions(repository);
    }

    private static List<Long> eventIds(RecordingEmitter emitter) {
        return emitter.frames.stream()
                .flatMap(List::stream)
                .filter(GovernanceEventStreamPayload.class::isInstance)
                .map(GovernanceEventStreamPayload.class::cast)
                .map(GovernanceEventStreamPayload::eventId)
                .toList();
    }

    private static GovernanceEventStreamPayload payload(AuditEventEntity entity) {
        return GovernanceEventStreamPayload.from(
                entity,
                com.agentops.guardian.governance.audit.dto.WorkflowTrajectoryEvent.from(entity)
        );
    }

    private static AuditEventEntity entity(long id, String workflowId) {
        AuditEventEntity entity = BeanUtils.instantiateClass(AuditEventEntity.class);
        ReflectionTestUtils.setField(entity, "id", id);
        ReflectionTestUtils.setField(entity, "workflowId", workflowId);
        ReflectionTestUtils.setField(entity, "agentName", "CustomerReportAgent");
        ReflectionTestUtils.setField(entity, "workflowType", WorkflowType.CUSTOMER_REPORTING);
        ReflectionTestUtils.setField(entity, "timestamp", Instant.parse("2026-10-06T10:00:00Z"));
        ReflectionTestUtils.setField(entity, "eventType", AuditEventType.POLICY_DECISION);
        ReflectionTestUtils.setField(entity, "toolCallId", "tool-call-" + id);
        ReflectionTestUtils.setField(entity, "toolName", "sendEmail");
        ReflectionTestUtils.setField(entity, "capability", WorkflowCapability.SEND_EMAIL);
        ReflectionTestUtils.setField(entity, "sequence", 1);
        ReflectionTestUtils.setField(entity, "policyDecision", DecisionType.BLOCK);
        ReflectionTestUtils.setField(entity, "policyId", "TEST_POLICY");
        ReflectionTestUtils.setField(entity, "policyReason", "Blocked by test policy.");
        return entity;
    }

    private static final class RecordingEmitter extends SseEmitter {
        private final List<List<Object>> frames = new ArrayList<>();
        private boolean failSends;
        private boolean completedWithError;

        private RecordingEmitter() {
            super();
        }

        private RecordingEmitter(Long timeout) {
            super(timeout);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failSends) {
                throw new IOException("Simulated disconnected client.");
            }
            frames.add(builder.build().stream()
                    .map(ResponseBodyEmitter.DataWithMediaType::getData)
                    .toList());
        }

        @Override
        public void completeWithError(Throwable failure) {
            completedWithError = true;
        }
    }
}
