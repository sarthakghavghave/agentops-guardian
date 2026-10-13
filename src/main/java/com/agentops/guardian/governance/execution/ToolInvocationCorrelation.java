package com.agentops.guardian.governance.execution;

import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.model.ToolInvocationOutcome;
import com.agentops.guardian.tool.CustomerDataTools;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ToolInvocationCorrelation {

    private static final Logger logger = LoggerFactory.getLogger(ToolInvocationCorrelation.class);

    private final ObservationRegistry observationRegistry;
    private final ConcurrentMap<ToolCallingObservationContext, ActiveInvocation> activeInvocations =
            new ConcurrentHashMap<>();

    public ToolInvocationCorrelation(ObservationRegistry observationRegistry) {
        if (observationRegistry == null) {
            throw new IllegalArgumentException("Observation registry is required.");
        }
        this.observationRegistry = observationRegistry;
        // Keep per-tool observation scopes available even when no exporter handles them.
        observationRegistry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return context instanceof ToolCallingObservationContext;
            }
        });
    }

    ObservationRegistry observationRegistry() {
        return observationRegistry;
    }

    int activeInvocationCount() {
        return activeInvocations.size();
    }

    InvocationBatch newInvocationBatch() {
        return new InvocationBatch();
    }

    List<ToolCallback> wrapCustomerDataCallbacks(
            List<ToolCallback> callbacks,
            List<InvocationReference> proposedCalls,
            WorkflowContext workflowContext,
            InvocationBatch invocationBatch
    ) {
        return callbacks.stream()
                .map(callback -> wrapCustomerDataCallback(
                        callback,
                        proposedCalls,
                        workflowContext,
                        invocationBatch
                ))
                .toList();
    }

    public void recordCustomerDataResult(CustomerDataTools.CustomerDataSet result) {
        if (result == null) {
            throw new IllegalArgumentException("Customer data result is required.");
        }

        Observation observation = observationRegistry.getCurrentObservation();
        if (observation == null
                || !(observation.getContext() instanceof ToolCallingObservationContext toolContext)) {
            return;
        }

        ActiveInvocation activeInvocation = activeInvocations.get(toolContext);
        if (activeInvocation != null
                && CustomerDataTools.TOOL_NAME.equals(toolContext.getToolDefinition().name())) {
            activeInvocation.record(result);
        }
    }

    private ToolCallback wrapCustomerDataCallback(
            ToolCallback callback,
            List<InvocationReference> proposedCalls,
            WorkflowContext workflowContext,
            InvocationBatch invocationBatch
    ) {
        if (!CustomerDataTools.TOOL_NAME.equals(callback.getToolDefinition().name())) {
            return callback;
        }

        return new ToolCallback() {
            @Override
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return callback.getToolDefinition();
            }

            @Override
            public org.springframework.ai.tool.metadata.ToolMetadata getToolMetadata() {
                return callback.getToolMetadata();
            }

            @Override
            public String call(String toolInput) {
                return call(toolInput, null);
            }

            @Override
            public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
                Observation observation = observationRegistry.getCurrentObservation();
                if (observation == null
                        || !(observation.getContext() instanceof ToolCallingObservationContext callContext)) {
                    warn("Customer data tool invocation has no Spring AI tool-call observation; "
                            + "result correlation is unavailable.");
                    return callback.call(toolInput, toolContext);
                }

                Optional<InvocationReference> reference = resolveReference(callContext, proposedCalls);
                if (reference.isEmpty()) {
                    warn("Customer data tool invocation could not be uniquely correlated to a proposed call.");
                    return callback.call(toolInput, toolContext);
                }

                ActiveInvocation activeInvocation = new ActiveInvocation(
                        reference.get(),
                        workflowContext,
                        invocationBatch
                );
                if (activeInvocations.putIfAbsent(callContext, activeInvocation) != null) {
                    warn("Customer data tool invocation observation is already active; "
                            + "result correlation is unavailable.");
                    return callback.call(toolInput, toolContext);
                }

                try {
                    String serializedResult = callback.call(toolInput, toolContext);
                    activeInvocation.recordCompletion();
                    return serializedResult;
                } catch (RuntimeException | Error exception) {
                    activeInvocation.recordFailure(exception);
                    throw exception;
                } finally {
                    activeInvocations.remove(callContext, activeInvocation);
                }
            }
        };
    }

    private void warn(String message) {
        logger.warn(message);
    }

    private Optional<InvocationReference> resolveReference(
            ToolCallingObservationContext callContext,
            List<InvocationReference> proposedCalls
    ) {
        String providerToolCallId = callContext.getToolCallId();
        if (providerToolCallId == null || providerToolCallId.isBlank()) {
            return Optional.empty();
        }
        List<InvocationReference> matches = proposedCalls.stream()
                .filter(reference -> CustomerDataTools.TOOL_NAME.equals(reference.toolName()))
                .filter(reference -> reference.toolName().equals(callContext.getToolDefinition().name()))
                .filter(reference -> providerToolCallId.equals(reference.guardianToolCallId())
                        || providerToolCallId.equals(reference.providerToolCallId()))
                .toList();

        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    record InvocationReference(String guardianToolCallId, String providerToolCallId, String toolName) {}

    static final class InvocationBatch {

        private final ConcurrentMap<String, ToolInvocationOutcome<?>> outcomesByToolCallId =
                new ConcurrentHashMap<>();

        Map<String, ToolInvocationOutcome<?>> outcomesByToolCallId() {
            return Map.copyOf(outcomesByToolCallId);
        }

        void recordOutcome(WorkflowContext workflowContext, ToolInvocationOutcome<?> outcome) {
            ToolInvocationOutcome<?> existing = outcomesByToolCallId.putIfAbsent(
                    outcome.toolCallId(),
                    outcome
            );
            if (existing != null) {
                throw new IllegalStateException(
                        "A tool invocation outcome was already recorded for tool call "
                                + outcome.toolCallId()
                );
            }
            try {
                workflowContext.recordToolInvocationOutcome(outcome);
            } catch (RuntimeException | Error exception) {
                outcomesByToolCallId.remove(outcome.toolCallId(), outcome);
                throw exception;
            }
        }
    }

    private static final class ActiveInvocation {

        private final InvocationReference reference;
        private final WorkflowContext workflowContext;
        private final InvocationBatch invocationBatch;
        private CustomerDataTools.CustomerDataSet typedResult;
        private boolean duplicateResult;

        private ActiveInvocation(
                InvocationReference reference,
                WorkflowContext workflowContext,
                InvocationBatch invocationBatch
        ) {
            this.reference = reference;
            this.workflowContext = workflowContext;
            this.invocationBatch = invocationBatch;
        }

        private void record(CustomerDataTools.CustomerDataSet result) {
            if (typedResult != null || duplicateResult) {
                duplicateResult = true;
                typedResult = null;
                return;
            }
            typedResult = result;
        }

        private void recordCompletion() {
            ToolInvocationOutcome.Status status = typedResult == null || duplicateResult
                    ? ToolInvocationOutcome.Status.UNCONFIRMED
                    : ToolInvocationOutcome.Status.SUCCEEDED;
            recordOutcome(new ToolInvocationOutcome<>(
                    reference.guardianToolCallId(),
                    reference.toolName(),
                    status,
                    typedResult,
                    null,
                    Instant.now()
            ));
        }

        private void recordFailure(Throwable exception) {
            recordOutcome(new ToolInvocationOutcome<>(
                    reference.guardianToolCallId(),
                    reference.toolName(),
                    ToolInvocationOutcome.Status.FAILED,
                    null,
                    exception.getClass().getName(),
                    Instant.now()
            ));
        }

        private void recordOutcome(ToolInvocationOutcome<?> outcome) {
            invocationBatch.recordOutcome(workflowContext, outcome);
        }
    }
}
