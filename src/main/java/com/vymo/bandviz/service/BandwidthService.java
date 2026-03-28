package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.Assignment;
import com.vymo.bandviz.domain.Developer;
import com.vymo.bandviz.domain.JiraTicket;
import com.vymo.bandviz.domain.enums.BandwidthStatus;
import com.vymo.bandviz.domain.enums.TicketStatus;
import com.vymo.bandviz.dto.response.DeveloperBandwidthResponse;
import com.vymo.bandviz.repository.AssignmentRepository;
import com.vymo.bandviz.repository.DeveloperRepository;
import com.vymo.bandviz.repository.JiraTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BandwidthService {

    private final DeveloperRepository developerRepository;
    private final AssignmentRepository assignmentRepository;
    private final LeaveService leaveService;
    private final JiraTicketRepository jiraTicketRepository;
    private final PlanningWindowService planningWindowService;

    @Value("${bandviz.bandwidth.overload-threshold:85}")
    private int overloadThreshold;

    @Value("${bandviz.bandwidth.busy-threshold:70}")
    private int busyThreshold;

    // Compute bandwidth for all active developers for a given date range
    public List<DeveloperBandwidthResponse> computeForPeriod(LocalDate startDate, LocalDate endDate) {
        List<Developer> developers = developerRepository.findAllByActiveTrue();
        long workingDaysInPeriod = leaveService.countWorkingDays(startDate, endDate);

        List<Assignment> allAssignments = assignmentRepository.findAllActiveInRange(startDate, endDate);

        // Group assignments by developer
        Map<Long, List<Assignment>> assignmentsByDev = allAssignments.stream()
                .collect(Collectors.groupingBy(a -> a.getDeveloper().getId()));

        Map<String, List<JiraTicket>> ticketsByAssignee = jiraTicketRepository
                .findAllByAssigneeJiraUsernameInAndStatusNot(
                        developers.stream()
                                .map(Developer::getJiraUsername)
                                .filter(username -> username != null && !username.isBlank())
                                .distinct()
                                .toList(),
                        TicketStatus.DONE
                )
                .stream()
                .collect(Collectors.groupingBy(ticket -> ticket.getAssigneeJiraUsername().trim()));

        return developers.stream()
                .map(dev -> buildBandwidthResponse(dev, assignmentsByDev.getOrDefault(dev.getId(), List.of()),
                        ticketsByAssignee.getOrDefault(dev.getJiraUsername(), List.of()),
                        startDate, endDate, workingDaysInPeriod))
                .toList();
    }

    // Compute bandwidth for the current planning window, falling back when no sprint is active.
    public List<DeveloperBandwidthResponse> computeForActiveWindow() {
        PlanningWindow window = planningWindowService.resolveCurrentWindow();
        return computeForPeriod(window.startDate(), window.endDate());
    }

    // Compute bandwidth for a specific sprint
    public List<DeveloperBandwidthResponse> computeForSprint(Long sprintId) {
        PlanningWindow window = planningWindowService.resolveSprintWindow(sprintId);
        return computeForPeriod(window.startDate(), window.endDate());
    }

    private DeveloperBandwidthResponse buildBandwidthResponse(
            Developer dev,
            List<Assignment> assignments,
            List<JiraTicket> tickets,
            LocalDate startDate,
            LocalDate endDate,
            long workingDaysInPeriod) {

        // Total allocation % = sum of active project assignments
        int totalAllocationPct = assignments.stream()
                .mapToInt(Assignment::getAllocationPct)
                .sum();

        // Leave days in period
        long leaveDays = leaveService.countApprovedLeaveDays(dev.getId(), startDate, endDate);

        // Effective bandwidth = allocation × (1 - leaveFraction)
        double leaveFraction = workingDaysInPeriod > 0
                ? (double) leaveDays / workingDaysInPeriod
                : 0.0;
        double effectivePct = totalAllocationPct * (1.0 - leaveFraction);

        // Jira stats (keyed by jiraUsername)
        long openTickets = 0;
        long blockedTickets = 0;
        int storyPoints = 0;
        if (dev.getJiraUsername() != null) {
            openTickets = tickets.size();
            blockedTickets = tickets.stream()
                    .filter(t -> t.getStatus() == TicketStatus.BLOCKED)
                    .count();
            storyPoints = tickets.stream()
                    .filter(t -> t.getStoryPoints() != null)
                    .mapToInt(t -> t.getStoryPoints())
                    .sum();
        }

        // Project allocation breakdown
        List<DeveloperBandwidthResponse.ProjectAllocationItem> projectItems = assignments.stream()
                .map(a -> DeveloperBandwidthResponse.ProjectAllocationItem.builder()
                        .projectId(a.getProject().getId())
                        .projectName(a.getProject().getName())
                        .projectColor(a.getProject().getColor())
                        .allocationPct(a.getAllocationPct())
                        .build())
                .toList();

        return DeveloperBandwidthResponse.builder()
                .developerId(dev.getId())
                .developerName(dev.getName())
                .role(dev.getRole())
                .jiraUsername(dev.getJiraUsername())
                .weeklyCapacityHours(dev.getWeeklyCapacityHours())
                .totalAllocationPct(totalAllocationPct)
                .effectiveBandwidthPct(Math.round(effectivePct * 10.0) / 10.0)
                .status(resolveStatus(effectivePct, leaveDays, workingDaysInPeriod))
                .leaveDaysInPeriod(leaveDays)
                .workingDaysInPeriod(workingDaysInPeriod)
                .openTickets(openTickets)
                .blockedTickets(blockedTickets)
                .totalStoryPoints(storyPoints)
                .projectAllocations(projectItems)
                .build();
    }

    private BandwidthStatus resolveStatus(double effectivePct, long leaveDays, long workingDays) {
        if (workingDays > 0 && leaveDays >= workingDays) return BandwidthStatus.ON_LEAVE;
        if (effectivePct > overloadThreshold) return BandwidthStatus.OVERLOADED;
        if (effectivePct > busyThreshold)    return BandwidthStatus.BUSY;
        return BandwidthStatus.AVAILABLE;
    }
}
