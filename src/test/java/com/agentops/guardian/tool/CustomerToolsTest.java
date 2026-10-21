package com.agentops.guardian.tool;

import com.agentops.guardian.governance.execution.ToolInvocationCorrelation;
import com.agentops.guardian.service.CustomerService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerToolsTest {

    @Test
    void listsDistinctCustomerCitiesAndTheirCount() {
        CustomerService customerService = mock(CustomerService.class);
        when(customerService.getDistinctCities()).thenReturn(List.of("Boston", "Seattle"));
        CustomerTools customerTools = new CustomerTools(
                customerService,
                mock(ToolInvocationCorrelation.class)
        );

        assertEquals(
                "Cities in customer records (2):\nBoston\nSeattle",
                customerTools.getCustomerCities()
        );
    }
}