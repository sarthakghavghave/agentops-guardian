package com.agentops.guardian.controller;

import com.agentops.guardian.agent.CustomerSupportAgent;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support-agent")
public class CustomerSupportController {

    private final CustomerSupportAgent agent;

    public CustomerSupportController(CustomerSupportAgent agent) {
        this.agent = agent;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        return agent.chat(message);
    }
}