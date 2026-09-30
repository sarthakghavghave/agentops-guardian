package com.agentops.guardian.governance.policy;

import com.agentops.guardian.governance.workflow.WorkflowCapability;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "governance_policies",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_governance_policies_policy_id",
                columnNames = "policy_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_id", nullable = false, length = 100)
    private String policyId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private int priority;

    @Column(name = "target_tool_name", length = 200)
    private String targetToolName;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_capability", length = 80)
    private WorkflowCapability targetCapability;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_field", nullable = false, length = 50)
    private PolicyConditionField conditionField;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_operator", nullable = false, length = 50)
    private PolicyConditionOperator conditionOperator;

    @Column(name = "condition_value", nullable = false, columnDefinition = "text")
    private String conditionValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PolicyAction action;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PolicyDefinitionEntity from(PolicyDefinition definition) {
        Objects.requireNonNull(definition, "Policy definition is required.");
        PolicyDefinitionEntity entity = new PolicyDefinitionEntity();
        entity.updateFrom(definition);
        return entity;
    }

    public void updateFrom(PolicyDefinition definition) {
        Objects.requireNonNull(definition, "Policy definition is required.");
        policyId = definition.policyId();
        name = definition.name();
        description = definition.description();
        enabled = definition.enabled();
        priority = definition.priority();
        targetToolName = definition.target().toolName();
        targetCapability = definition.target().capability();
        conditionField = definition.conditionField();
        conditionOperator = definition.conditionOperator();
        conditionValue = definition.conditionValue();
        action = definition.action();
    }

    public PolicyDefinition toDomain() {
        return new PolicyDefinition(
                policyId,
                name,
                description,
                enabled,
                priority,
                new PolicyTarget(targetToolName, targetCapability),
                conditionField,
                conditionOperator,
                conditionValue,
                action
        );
    }

    @PrePersist
    void setCreationTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void setUpdatedTimestamp() {
        updatedAt = Instant.now();
    }
}