package com.agentops.guardian.governance.workflow;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Component
public class WorkflowDefinitionRegistry {

    private final Map<WorkflowType, WorkflowDefinition> definitions = new EnumMap<>(WorkflowType.class);

    public WorkflowDefinitionRegistry() {
        definitions.put(
                WorkflowType.CUSTOMER_REPORTING,
                new WorkflowDefinition(
                        WorkflowType.CUSTOMER_REPORTING,
                        Set.of(
                                WorkflowCapability.READ_CUSTOMER_DATA,
                                WorkflowCapability.GENERATE_REPORT,
                                WorkflowCapability.SEND_EMAIL
                        ),
                        WorkflowGraph.customerReportingGraph()
                )
        );

        WorkflowGraph customerSupportGraph = WorkflowGraph.builder()
                .addNode(WorkflowGraph.START_NODE_ID)
                .addNode("CUSTOMER_DATA", WorkflowCapability.READ_CUSTOMER_DATA)
                .addNode("ORDER", WorkflowCapability.READ_ORDER)
                .addNode("PRODUCT", WorkflowCapability.READ_PRODUCT)
                .addNode("RETURN_ORDER", WorkflowCapability.RETURN_ORDER)
                .addTransition(WorkflowGraph.START_NODE_ID, "CUSTOMER_DATA")
                .addTransition(WorkflowGraph.START_NODE_ID, "ORDER")
                .addTransition(WorkflowGraph.START_NODE_ID, "PRODUCT")
                .addTransition("CUSTOMER_DATA", "ORDER")
                .addTransition("CUSTOMER_DATA", "PRODUCT")
                .addTransition("ORDER", "RETURN_ORDER")
                .addTransition("ORDER", "PRODUCT")
                .addTransition("PRODUCT", "ORDER")
                .build();

        definitions.put(
                WorkflowType.CUSTOMER_SUPPORT,
                new WorkflowDefinition(
                        WorkflowType.CUSTOMER_SUPPORT,
                        Set.of(
                                WorkflowCapability.READ_CUSTOMER_DATA,
                                WorkflowCapability.READ_ORDER,
                                WorkflowCapability.READ_PRODUCT,
                                WorkflowCapability.RETURN_ORDER
                        ),
                        customerSupportGraph
                )
        );
    }

    public WorkflowDefinition get(WorkflowType type) {
        WorkflowDefinition definition = definitions.get(type);

        if (definition == null) {
            throw new IllegalArgumentException("No workflow definition registered for: " + type);
        }

        return definition;
    }
}