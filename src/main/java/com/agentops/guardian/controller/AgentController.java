package com.agentops.guardian.controller;

import com.agentops.guardian.agent.CustomerSupportAgent;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final CustomerSupportAgent agent;

    public AgentController(CustomerSupportAgent agent) {
        this.agent = agent;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        return agent.chat(message);
    }
}