package com.vymo.bandviz.controller;

import com.vymo.bandviz.dto.response.DashboardResponse;
import com.vymo.bandviz.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/dashboard-summary", "/api/dashboard"})
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Aggregated dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Get dashboard summary")
    public DashboardResponse get(@RequestParam(required = false) Long sprintId) {
        if (sprintId != null) {
            return dashboardService.getForSprint(sprintId);
        }
        return dashboardService.getActiveSprint();
    }
}
