package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.enums.BandwidthStatus;
import com.vymo.bandviz.domain.enums.TicketStatus;
import com.vymo.bandviz.dto.response.DashboardResponse;
import com.vymo.bandviz.dto.response.DeveloperBandwidthResponse;
import com.vymo.bandviz.repository.JiraTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final BandwidthService bandwidthService;
    private final JiraTicketRepository jiraTicketRepository;
    private final PlanningWindowService planningWindowService;

    public DashboardResponse getActiveSprint() {
        PlanningWindow window = planningWindowService.resolveCurrentWindow();
        List<DeveloperBandwidthResponse> devBandwidths = bandwidthService.computeForActiveWindow();
        return buildResponse(window, devBandwidths);
    }

    public DashboardResponse getForSprint(Long sprintId) {
        PlanningWindow window = planningWindowService.resolveSprintWindow(sprintId);
        List<DeveloperBandwidthResponse> devBandwidths = bandwidthService.computeForSprint(sprintId);
        return buildResponse(window, devBandwidths);
    }

    private DashboardResponse buildResponse(PlanningWindow window, List<DeveloperBandwidthResponse> devBandwidths) {
        int total = devBandwidths.size();
        int available  = (int) devBandwidths.stream().filter(d -> d.getStatus() == BandwidthStatus.AVAILABLE).count();
        int busy       = (int) devBandwidths.stream().filter(d -> d.getStatus() == BandwidthStatus.BUSY).count();
        int overloaded = (int) devBandwidths.stream().filter(d -> d.getStatus() == BandwidthStatus.OVERLOADED).count();
        int onLeave    = (int) devBandwidths.stream().filter(d -> d.getStatus() == BandwidthStatus.ON_LEAVE).count();

        double avgUtilization = devBandwidths.stream()
                .mapToDouble(DeveloperBandwidthResponse::getEffectiveBandwidthPct)
                .average()
                .orElse(0.0);

        long openTickets    = jiraTicketRepository.countByStatus(TicketStatus.IN_PROGRESS)
                            + jiraTicketRepository.countByStatus(TicketStatus.TO_DO)
                            + jiraTicketRepository.countByStatus(TicketStatus.IN_REVIEW)
                            + jiraTicketRepository.countByStatus(TicketStatus.BLOCKED);
        long blockedTickets = jiraTicketRepository.countByStatus(TicketStatus.BLOCKED);
        long closedTickets  = jiraTicketRepository.countByStatusAndLastSyncedAtGreaterThanEqual(
                TicketStatus.DONE,
                window.startDate().atStartOfDay()
        );

        List<DashboardResponse.AlertItem> alerts = buildAlerts(devBandwidths, overloaded);

        return DashboardResponse.builder()
                .sprintName(window.name())
                .sprintStart(window.startDate())
                .sprintEnd(window.endDate())
                .planningMode(window.mode())
                .usingFallbackWindow(window.fallback())
                .totalDevelopers(total)
                .availableCount(available)
                .busyCount(busy)
                .overloadedCount(overloaded)
                .onLeaveCount(onLeave)
                .averageUtilizationPct(Math.round(avgUtilization * 10.0) / 10.0)
                .totalOpenTickets(openTickets)
                .totalBlockedTickets(blockedTickets)
                .totalClosedThisSprint(closedTickets)
                .totalClosedInWindow(closedTickets)
                .alerts(alerts)
                .developers(devBandwidths)
                .build();
    }

    private List<DashboardResponse.AlertItem> buildAlerts(
            List<DeveloperBandwidthResponse> devBandwidths, int overloadedCount) {

        List<DashboardResponse.AlertItem> alerts = new ArrayList<>();

        if (overloadedCount > 0) {
            alerts.add(DashboardResponse.AlertItem.builder()
                    .severity("HIGH")
                    .message(overloadedCount + " developer(s) are overloaded. Review bandwidth allocations.")
                    .build());
        }

        devBandwidths.stream()
                .filter(d -> d.getBlockedTickets() > 0)
                .forEach(d -> alerts.add(DashboardResponse.AlertItem.builder()
                        .severity("HIGH")
                        .message(d.getDeveloperName() + " has " + d.getBlockedTickets() + " blocked ticket(s).")
                        .build()));

        devBandwidths.stream()
                .filter(d -> d.getStatus() == BandwidthStatus.ON_LEAVE && d.getOpenTickets() > 0)
                .forEach(d -> alerts.add(DashboardResponse.AlertItem.builder()
                        .severity("MEDIUM")
                        .message(d.getDeveloperName() + " is on leave with " + d.getOpenTickets() + " open ticket(s). Consider reassigning.")
                        .build()));

        return alerts;
    }
}
