package com.agentops.guardian.controller;

import com.agentops.guardian.governance.config.GovernanceSettings;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/governance/settings")
public class GovernanceSettingsController {

    private final GovernanceSettings governanceSettings;

    public GovernanceSettingsController(GovernanceSettings governanceSettings) {
        this.governanceSettings = governanceSettings;
    }

    @GetMapping
    public GovernanceSettingsResponse getSettings() {
        return new GovernanceSettingsResponse(governanceSettings.isEnabled());
    }

    @PutMapping
    public GovernanceSettingsResponse updateSettings(
            @RequestBody GovernanceSettingsUpdate request
    ) {
        governanceSettings.setEnabled(request.enabled());
        return getSettings();
    }

    public record GovernanceSettingsResponse(boolean enabled) {}

    public record GovernanceSettingsUpdate(boolean enabled) {}
}
