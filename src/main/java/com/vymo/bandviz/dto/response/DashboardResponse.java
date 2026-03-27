package com.vymo.bandviz.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class DashboardResponse {

    // Sprint context
    private String sprintName;
    private LocalDate sprintStart;
    private LocalDate sprintEnd;
    private String planningMode;
    private Boolean usingFallbackWindow;

    // Team summary
    private Integer totalDevelopers;
    private Integer availableCount;
    private Integer busyCount;
    private Integer overloadedCount;
    private Integer onLeaveCount;
    private Double averageUtilizationPct;

    // Jira summary
    private Long totalOpenTickets;
    private Long totalBlockedTickets;
    private Long totalClosedThisSprint;
    private Long totalClosedInWindow;

    // Alerts
    private List<AlertItem> alerts;

    // Per-developer bandwidth
    private List<DeveloperBandwidthResponse> developers;

    @Data
    @Builder
    public static class AlertItem {
        private String severity;   // HIGH, MEDIUM, INFO
        private String message;
    }
}
