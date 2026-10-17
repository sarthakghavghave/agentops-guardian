package com.agentops.guardian.governance.execution;

import com.agentops.guardian.governance.config.GovernanceSettings;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.ToolCallingManager;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GovernanceRuntimeToggleTest {

    @Test
    void toolCallingManagerUsesTheCurrentRuntimeGovernanceSetting() {
        GovernanceSettings settings = new GovernanceSettings();
        settings.setEnabled(true);
        ToolCallingManager delegate = mock(ToolCallingManager.class);
        GuardianToolCallingManager manager = new GuardianToolCallingManager(
                null,
                null,
                null,
                delegate,
                null,
                null,
                null,
                null,
                null,
                settings,
                new ApprovedActionGrant()
        );

        settings.setEnabled(false);

        assertNull(manager.executeToolCalls(null, null));
        verify(delegate).executeToolCalls(null, null);
    }
}
