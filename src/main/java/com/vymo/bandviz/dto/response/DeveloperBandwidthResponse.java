package com.vymo.bandviz.dto.response;

import com.vymo.bandviz.domain.enums.BandwidthStatus;
import com.vymo.bandviz.domain.enums.DeveloperRole;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DeveloperBandwidthResponse {
    private Long developerId;
    private String developerName;
    private DeveloperRole role;
    private String jiraUsername;

    // Capacity
    private Integer weeklyCapacityHours;
    private Integer totalAllocationPct;       // sum of all active assignment %
    private Double effectiveBandwidthPct;     // allocationPct × (1 - leaveFraction)
    private BandwidthStatus status;

    // Leave in the period
    private Long leaveDaysInPeriod;
    private Long workingDaysInPeriod;

    // Jira stats
    private Long openTickets;
    private Long blockedTickets;
    private Integer totalStoryPoints;

    // Per-project breakdown
    private List<ProjectAllocationItem> projectAllocations;

    @Data
    @Builder
    public static class ProjectAllocationItem {
        private Long projectId;
        private String projectName;
        private String projectColor;
        private Integer allocationPct;
    }
}
