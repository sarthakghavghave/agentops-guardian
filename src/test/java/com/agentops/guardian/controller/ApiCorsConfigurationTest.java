package com.agentops.guardian.controller;

import com.agentops.guardian.config.ApiCorsConfiguration;
import com.agentops.guardian.governance.audit.live.LiveGovernanceEventPublisher;
import com.agentops.guardian.service.PolicyManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PolicyManagementController.class, GovernanceEventStreamController.class})
@Import(ApiCorsConfiguration.class)
class ApiCorsConfigurationTest {

    private static final String FRONTEND_ORIGIN = "https://guardian.example";
    private static final String PORTAL_ORIGIN = "https://portal.example";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PolicyManagementService policyService;

    @MockitoBean
    private LiveGovernanceEventPublisher publisher;

    @Test
    void allowsRequestsFromBothConfiguredFrontendOrigins() throws Exception {
        when(policyService.getAll()).thenReturn(List.of());

        for (String origin : List.of(FRONTEND_ORIGIN, PORTAL_ORIGIN)) {
            mockMvc.perform(get("/api/policies").header("Origin", origin))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin))
                    .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        }
    }

    @Test
    void rejectsUnconfiguredOrigin() throws Exception {
        when(policyService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/policies").header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void handlesJsonPreflightForPost() throws Exception {
        mockMvc.perform(options("/api/policies")
                        .header("Origin", FRONTEND_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,accept,last-event-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONTEND_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT"))
                .andExpect(header().string(
                        "Access-Control-Allow-Headers",
                        allOf(containsString("content-type"), containsString("last-event-id"))
                ))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void allowsSseConnectionAndReconnectCursor() throws Exception {
        when(publisher.connect("event-42")).thenReturn(new SseEmitter(0L));

        mockMvc.perform(get("/api/events/stream")
                        .header("Origin", PORTAL_ORIGIN)
                        .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                        .header("Last-Event-ID", "event-42"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", PORTAL_ORIGIN))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(request().asyncStarted());
        verify(publisher).connect("event-42");
    }

    @org.springframework.test.context.DynamicPropertySource
    static void corsOrigins(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add(
                "agentops.cors.allowed-origins",
                () -> FRONTEND_ORIGIN + "," + PORTAL_ORIGIN
        );
    }
}
