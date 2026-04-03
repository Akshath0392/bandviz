package com.vymo.bandviz.controller;

import com.vymo.bandviz.service.JiraSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

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

    @GetMapping("/filters")
    @Operation(summary = "Get jira sync filters")
    public JiraSyncService.SyncFiltersResponse filters() {
        return jiraSyncService.getFilters();
    }

    @PutMapping("/filters/global")
    @Operation(summary = "Save global jira sync filters")
    public JiraSyncService.SyncFilterPayload saveGlobalFilter(@RequestBody JiraSyncService.SyncFilterPayload payload) {
        return jiraSyncService.saveGlobalFilter(payload);
    }

    @PutMapping("/filters/projects/{projectKey}")
    @Operation(summary = "Save per-project jira sync filters")
    public JiraSyncService.SyncFilterPayload saveProjectFilter(
            @PathVariable String projectKey,
            @RequestBody JiraSyncService.SyncFilterPayload payload
    ) {
        return jiraSyncService.saveProjectFilter(projectKey, payload);
    }

    @DeleteMapping("/filters/global")
    @Operation(summary = "Reset global jira sync filters")
    public void resetGlobalFilter() {
        jiraSyncService.resetGlobalFilter();
    }

    @DeleteMapping("/filters/projects/{projectKey}")
    @Operation(summary = "Reset per-project jira sync filters")
    public void resetProjectFilter(@PathVariable String projectKey) {
        jiraSyncService.resetProjectFilter(projectKey);
    }

    @GetMapping("/filters/preview-jql")
    @Operation(summary = "Preview effective jql for a project")
    public JiraSyncService.JqlPreviewResponse previewJql(@RequestParam String projectKey) {
        return jiraSyncService.previewJql(projectKey);
    }
}
