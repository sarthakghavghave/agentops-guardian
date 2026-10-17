package com.agentops.guardian.controller;

import com.agentops.guardian.governance.config.GovernanceSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GovernanceSettingsController.class)
class GovernanceSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GovernanceSettings governanceSettings;

    @Test
    void returnsCurrentGovernanceSetting() throws Exception {
        when(governanceSettings.isEnabled()).thenReturn(true);

        mockMvc.perform(get("/api/governance/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void updatesAndReturnsGovernanceSetting() throws Exception {
        when(governanceSettings.isEnabled()).thenReturn(false);

        mockMvc.perform(put("/api/governance/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        verify(governanceSettings).setEnabled(false);
    }
}
