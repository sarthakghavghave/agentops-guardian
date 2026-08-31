package com.agentops.guardian.controller;

import com.agentops.guardian.agent.CustomerReportAgent;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/report-agent")
@Validated
public class CustomerReportController {

    private final CustomerReportAgent reportAgent;

    public CustomerReportController(CustomerReportAgent reportAgent) {
        this.reportAgent = reportAgent;
    }

    @PostMapping("/run")
    public ResponseEntity<String> runReport(
            @RequestParam
            @NotBlank(message = "Request must not be empty")
            String request) {

        return ResponseEntity.ok(reportAgent.generateReport(request));
    }
}