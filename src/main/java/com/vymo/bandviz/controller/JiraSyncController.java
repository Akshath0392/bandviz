package com.vymo.bandviz.controller;

import com.vymo.bandviz.service.JiraSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/jira-sync", "/api/jira"})
@RequiredArgsConstructor
@Tag(name = "Jira", description = "Jira sync operations")
public class JiraSyncController {

    private final JiraSyncService jiraSyncService;

    @PostMapping({"/runs", "/sync"})
    @Operation(summary = "Run Jira sync now")
    public JiraSyncService.SyncResult sync() {
        return jiraSyncService.syncAll();
    }

    @GetMapping("/status")
    @Operation(summary = "Get Jira sync status")
    public JiraSyncService.SyncStatusResponse status() {
        return jiraSyncService.getStatus();
    }

    @GetMapping("/projects")
    @Operation(summary = "Get Jira project sync mapping status")
    public List<JiraSyncService.ProjectSyncStatusResponse> projects() {
        return jiraSyncService.getProjectMappings();
    }
}
