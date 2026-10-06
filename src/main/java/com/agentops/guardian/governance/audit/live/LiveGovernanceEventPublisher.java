package com.agentops.guardian.governance.audit.live;

import com.agentops.guardian.governance.audit.AuditEventEntity;
import com.agentops.guardian.governance.audit.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class LiveGovernanceEventPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(LiveGovernanceEventPublisher.class);
    private static final long HEARTBEAT_INTERVAL_SECONDS = 20;

    private final AuditEventRepository repository;
    private final TaskExecutor deliveryExecutor;
    private final Map<SseEmitter, ClientSession> clients = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "governance-sse-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    public LiveGovernanceEventPublisher(
            AuditEventRepository repository,
            @Qualifier("liveGovernanceEventExecutor") TaskExecutor deliveryExecutor
    ) {
        this.repository = repository;
        this.deliveryExecutor = deliveryExecutor;
    }

    @PostConstruct
    void startHeartbeats() {
        heartbeatScheduler.scheduleAtFixedRate(
                this::sendHeartbeats,
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    @PreDestroy
    void stop() {
        heartbeatScheduler.shutdownNow();
        clients.forEach((emitter, session) -> session.complete());
        clients.clear();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuditEventCommitted(GovernanceAuditEventPersisted persistedEvent) {
        for (ClientSession session : clients.values()) {
            try {
                deliveryExecutor.execute(() -> session.offer(persistedEvent.payload()));
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not queue a governance event for an SSE client; it can be replayed after reconnect.");
            }
        }
    }

    public SseEmitter connect(String lastEventIdHeader) {
        return connect(lastEventIdHeader, new SseEmitter(0L));
    }

    SseEmitter connect(String lastEventIdHeader, SseEmitter emitter) {
        Long cursor = parseCursor(lastEventIdHeader);
        ClientSession session = new ClientSession(emitter, cursor == null ? -1 : cursor);
        clients.put(emitter, session);
        emitter.onCompletion(() -> removeClient(emitter));
        emitter.onTimeout(() -> removeClient(emitter));
        emitter.onError(ignored -> removeClient(emitter));

        if (lastEventIdHeader != null && cursor == null) {
            LOGGER.warn("Ignoring invalid Last-Event-ID header; opening a fresh governance event stream.");
        } else if (cursor != null) {
            LOGGER.info("Governance SSE replay requested.");
        }

        try {
            List<GovernanceEventStreamPayload> replay = cursor == null
                    ? List.of()
                    : repository.findAllByIdGreaterThanOrderByIdAsc(cursor).stream()
                    .map(LiveGovernanceEventPublisher::toPayload)
                    .toList();
            session.finishReplay(replay);
            if (cursor != null) {
                LOGGER.info("Governance SSE replay completed with {} persisted event(s).", replay.size());
            }
        } catch (RuntimeException exception) {
            removeClient(emitter);
            emitter.completeWithError(exception);
            throw exception;
        }

        LOGGER.info("Governance SSE client connected.");
        return emitter;
    }

    private static Long parseCursor(String header) {
        if (header == null) {
            return null;
        }
        try {
            long cursor = Long.parseLong(header.trim());
            return cursor >= 0 ? cursor : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static GovernanceEventStreamPayload toPayload(AuditEventEntity entity) {
        return GovernanceEventStreamPayload.from(
                entity,
                com.agentops.guardian.governance.audit.dto.WorkflowTrajectoryEvent.from(entity)
        );
    }

    void sendHeartbeats() {
        for (ClientSession session : clients.values()) {
            try {
                deliveryExecutor.execute(session::heartbeat);
            } catch (RuntimeException exception) {
                LOGGER.debug("Skipped an SSE heartbeat because the delivery executor is busy.");
            }
        }
    }

    private void removeClient(SseEmitter emitter) {
        if (clients.remove(emitter) != null) {
            LOGGER.info("Governance SSE client disconnected.");
        }
    }

    private final class ClientSession {
        private final SseEmitter emitter;
        private final Map<Long, GovernanceEventStreamPayload> pendingDuringReplay = new TreeMap<>();
        private boolean replaying = true;
        private long lastDeliveredId = -1;
        private boolean closed;

        private ClientSession(SseEmitter emitter, long cursor) {
            this.emitter = emitter;
            this.lastDeliveredId = cursor;
        }

        private synchronized void offer(GovernanceEventStreamPayload payload) {
            if (closed || payload.eventId() == null || payload.eventId() <= lastDeliveredId) {
                return;
            }
            if (replaying) {
                pendingDuringReplay.put(payload.eventId(), payload);
                return;
            }
            send(payload);
        }

        private synchronized void finishReplay(List<GovernanceEventStreamPayload> replay) {
            if (closed) {
                return;
            }
            List<GovernanceEventStreamPayload> ordered = new ArrayList<>(replay);
            ordered.sort((left, right) -> Long.compare(left.eventId(), right.eventId()));
            ordered.forEach(this::send);
            pendingDuringReplay.values().forEach(this::send);
            pendingDuringReplay.clear();
            replaying = false;
        }

        private synchronized void heartbeat() {
            if (closed) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException exception) {
                closeAfterFailure(exception);
            }
        }

        private synchronized void send(GovernanceEventStreamPayload payload) {
            if (closed || payload.eventId() == null || payload.eventId() <= lastDeliveredId) {
                return;
            }
            try {
                emitter.send(SseEmitter.event()
                        .name("governance-event")
                        .id(payload.eventId().toString())
                        .data(payload, MediaType.APPLICATION_JSON));
                lastDeliveredId = payload.eventId();
            } catch (IOException | IllegalStateException exception) {
                closeAfterFailure(exception);
            }
        }

        private synchronized void complete() {
            if (!closed) {
                closed = true;
                emitter.complete();
            }
        }

        private void closeAfterFailure(Exception exception) {
            if (!closed) {
                closed = true;
                clients.remove(emitter);
                LOGGER.warn("Governance SSE client delivery failed; disconnected client removed.");
                emitter.completeWithError(exception);
            }
        }
    }
}
