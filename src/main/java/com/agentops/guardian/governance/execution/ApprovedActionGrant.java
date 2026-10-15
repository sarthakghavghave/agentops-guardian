package com.agentops.guardian.governance.execution;

import com.agentops.guardian.governance.context.WorkflowContext;
import com.agentops.guardian.governance.intervention.GovernanceIntervention;
import com.agentops.guardian.governance.intervention.GovernanceInterventionStatus;
import com.agentops.guardian.governance.workflow.WorkflowCapability;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.HexFormat;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;

@Component
public class ApprovedActionGrant {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private final ThreadLocal<Grant> current = new ThreadLocal<>();

    public <T> T resume(GovernanceIntervention intervention, Supplier<T> task) {
        if (intervention == null || intervention.status() != GovernanceInterventionStatus.APPROVED
                || task == null) {
            throw new IllegalArgumentException("An approved intervention and resume task are required.");
        }
        Grant previous = current.get();
        current.set(new Grant(intervention));
        try {
            return task.get();
        } finally {
            if (previous == null) {
                current.remove();
            } else {
                current.set(previous);
            }
        }
    }

    public boolean consume(
            WorkflowContext workflow,
            WorkflowCapability capability,
            String toolName,
            String arguments
    ) {
        Grant grant = current.get();
        if (grant == null || grant.consumed
                || workflow == null
                || workflow.getWorkflowType() != grant.intervention.workflowType()
                || !Objects.equals(workflow.getConversationId(), grant.intervention.conversationId())
                || !workflow.getAgentName().equals(grant.intervention.agentName())
                || capability != grant.intervention.capability()
                || !Objects.equals(toolName, grant.intervention.toolName())
                || !(Objects.equals(argumentsDigest(arguments), grant.intervention.argumentsDigest())
                || Objects.equals(rawArgumentsDigest(arguments), grant.intervention.argumentsDigest()))) {
            return false;
        }
        grant.consumed = true;
        return true;
    }

    public String currentInterventionId() {
        Grant grant = current.get();
        return grant == null ? null : grant.intervention.interventionId();
    }

    public static String argumentsDigest(String arguments) {
        String value = arguments == null ? "" : arguments;
        try {
            JsonNode parsed = JSON_MAPPER.readTree(value);
            if (parsed != null) {
                value = JSON_MAPPER.writeValueAsString(canonicalize(parsed));
            }
        } catch (JsonProcessingException ignored) {
            // Preserve exact matching for malformed tool arguments; execution validation handles them.
        }
        return sha256(value);
    }

    private static String rawArgumentsDigest(String arguments) {
        return sha256(arguments == null ? "" : arguments);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static JsonNode canonicalize(JsonNode value) {
        if (value.isObject()) {
            ObjectNode normalized = JsonNodeFactory.instance.objectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            Iterator<java.util.Map.Entry<String, JsonNode>> iterator = value.fields();
            while (iterator.hasNext()) {
                java.util.Map.Entry<String, JsonNode> field = iterator.next();
                fields.put(field.getKey(), field.getValue());
            }
            fields.forEach((name, fieldValue) -> normalized.set(name, canonicalize(fieldValue)));
            return normalized;
        }
        if (value.isArray()) {
            ArrayNode normalized = JsonNodeFactory.instance.arrayNode();
            value.forEach(element -> normalized.add(canonicalize(element)));
            return normalized;
        }
        return value;
    }

    private static final class Grant {
        private final GovernanceIntervention intervention;
        private boolean consumed;

        private Grant(GovernanceIntervention intervention) {
            this.intervention = intervention;
        }
    }
}
