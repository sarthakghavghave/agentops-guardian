package com.agentops.guardian.governance.audit.live;

import com.agentops.guardian.governance.audit.AuditEvent;
import com.agentops.guardian.governance.audit.AuditEventEntity;
import com.agentops.guardian.governance.audit.AuditEventRepository;
import com.agentops.guardian.governance.audit.PostgreSQLAuditService;
import com.agentops.guardian.governance.workflow.WorkflowType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.core.task.TaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GovernanceAuditEventTransactionTest {
    private final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(TestConfiguration.class);

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void publishesOnlyAfterCommitAndNeverAfterRollback() {
        RecordingEmitter emitter = new RecordingEmitter();
        LiveGovernanceEventPublisher publisher = context.getBean(LiveGovernanceEventPublisher.class);
        PostgreSQLAuditService auditService = context.getBean(PostgreSQLAuditService.class);
        PlatformTransactionManager transactionManager = context.getBean(PlatformTransactionManager.class);
        publisher.connect(null, emitter);

        new TransactionTemplate(transactionManager).execute(status -> {
            auditService.record(event("workflow-commit"));
            assertEquals(0, emitter.eventCount());
            return null;
        });

        assertEquals(1, emitter.eventCount());

        new TransactionTemplate(transactionManager).execute(status -> {
            auditService.record(event("workflow-rollback"));
            status.setRollbackOnly();
            return null;
        });

        assertEquals(1, emitter.eventCount());
    }

    private static AuditEvent event(String workflowId) {
        return AuditEvent.proposedAction(
                workflowId,
                "CustomerReportAgent",
                WorkflowType.CUSTOMER_REPORTING,
                "call-1",
                "sendEmail",
                com.agentops.guardian.governance.workflow.WorkflowCapability.SEND_EMAIL,
                1
        );
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {
        @Bean
        AuditEventRepository auditEventRepository() {
            AuditEventRepository repository = mock(AuditEventRepository.class);
            when(repository.save(any(AuditEventEntity.class))).thenAnswer(invocation -> {
                AuditEventEntity entity = invocation.getArgument(0);
                ReflectionTestUtils.setField(entity, "id", 200L);
                return entity;
            });
            return repository;
        }

        @Bean(name = "liveGovernanceEventExecutor")
        TaskExecutor liveGovernanceEventExecutor() {
            return Runnable::run;
        }

        @Bean
        LiveGovernanceEventPublisher liveGovernanceEventPublisher(AuditEventRepository repository) {
            return new LiveGovernanceEventPublisher(repository, liveGovernanceEventExecutor());
        }

        @Bean
        PostgreSQLAuditService postgreSQLAuditService(
                AuditEventRepository repository,
                org.springframework.context.ApplicationEventPublisher eventPublisher
        ) {
            return new PostgreSQLAuditService(repository, eventPublisher);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new TestTransactionManager();
        }
    }

    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    private static final class RecordingEmitter extends SseEmitter {
        private final List<Object> data = new ArrayList<>();

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            builder.build().forEach(part -> data.add(part.getData()));
        }

        private long eventCount() {
            return data.stream().filter(GovernanceEventStreamPayload.class::isInstance).count();
        }
    }
}
