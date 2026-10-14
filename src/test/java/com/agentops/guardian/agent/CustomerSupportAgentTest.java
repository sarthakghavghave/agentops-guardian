package com.agentops.guardian.agent;

import com.agentops.guardian.coordinator.ConversationMemory;
import com.agentops.guardian.coordinator.context.SharedContextService;
import com.agentops.guardian.governance.context.WorkflowContextManager;
import com.agentops.guardian.tool.CustomerDataTools;
import com.agentops.guardian.tool.CustomerTools;
import com.agentops.guardian.tool.OrderTools;
import com.agentops.guardian.tool.ProductTools;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CustomerSupportAgentTest {

    @Test
    void usesSharedConversationHistoryAndRegistersCustomerRetrievalTools() throws IOException {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.defaultTools(any(Object[].class))).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        when(client.prompt().messages(anyList()).call().content())
                .thenReturn("Retrieved matching customer information.");
        ChatClient.ChatClientRequestSpec requestSpec = client.prompt();

        SharedContextService memory = new ConversationMemory();
        UUID conversationId = memory.open(null);
        memory.recordTurn(
                conversationId,
                "I am asking about customers in North Michael.",
                "Do you want specific customer information or an analytical report?"
        );

        CustomerTools customerTools = mock(CustomerTools.class);
        OrderTools orderTools = mock(OrderTools.class);
        ProductTools productTools = mock(ProductTools.class);
        CustomerDataTools customerDataTools = mock(CustomerDataTools.class);
        WorkflowContextManager workflowContextManager = mock(WorkflowContextManager.class);
        CustomerSupportAgent agent = new CustomerSupportAgent(
                builder,
                customerTools,
                orderTools,
                productTools,
                customerDataTools,
                workflowContextManager,
                memory
        );

        clearInvocations(requestSpec);
        assertEquals(
                "Retrieved matching customer information.",
                agent.chat(conversationId, "Give me the customer data.")
        );
        verify(workflowContextManager).start(
                eq("CustomerSupportAgent"),
                eq(com.agentops.guardian.governance.workflow.WorkflowType.CUSTOMER_SUPPORT),
                eq("Give me the customer data."),
                eq(conversationId)
        );

        ArgumentCaptor<Object[]> registeredTools = ArgumentCaptor.forClass(Object[].class);
        verify(builder).defaultTools(registeredTools.capture());
        assertTrue(List.of(registeredTools.getValue()).contains(customerDataTools));

        ArgumentCaptor<List<Message>> messages = ArgumentCaptor.forClass(List.class);
        verify(requestSpec).messages(messages.capture());
        assertEquals(
                List.of(
                        "I am asking about customers in North Michael.",
                        "Do you want specific customer information or an analytical report?",
                        "Give me the customer data."
                ),
                messages.getValue().stream().map(Message::getText).toList()
        );
    }
}
