package com.agentops.guardian.controller;

import com.agentops.guardian.governance.audit.live.LiveGovernanceEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
public class GovernanceEventStreamController {
    private final LiveGovernanceEventPublisher publisher;

    public GovernanceEventStreamController(LiveGovernanceEventPublisher publisher) {
        this.publisher = publisher;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId
    ) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(publisher.connect(lastEventId));
    }
}
