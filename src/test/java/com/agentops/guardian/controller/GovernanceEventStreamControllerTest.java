package com.agentops.guardian.controller;

import com.agentops.guardian.governance.audit.live.LiveGovernanceEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GovernanceEventStreamController.class)
class GovernanceEventStreamControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LiveGovernanceEventPublisher publisher;

    @Test
    void establishesSseConnectionAndPassesReconnectCursor() throws Exception {
        when(publisher.connect("1847")).thenReturn(new SseEmitter(0L));

        MvcResult result = mockMvc.perform(get("/api/events/stream")
                        .header("Last-Event-ID", "1847"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertTrue(result.getRequest().isAsyncStarted());
        verify(publisher).connect("1847");
    }
}
