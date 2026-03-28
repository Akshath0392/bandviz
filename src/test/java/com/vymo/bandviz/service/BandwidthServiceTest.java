package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.Assignment;
import com.vymo.bandviz.domain.Developer;
import com.vymo.bandviz.domain.JiraTicket;
import com.vymo.bandviz.domain.Project;
import com.vymo.bandviz.domain.enums.BandwidthStatus;
import com.vymo.bandviz.domain.enums.DeveloperRole;
import com.vymo.bandviz.domain.enums.TicketStatus;
import com.vymo.bandviz.dto.response.DeveloperBandwidthResponse;
import com.vymo.bandviz.repository.AssignmentRepository;
import com.vymo.bandviz.repository.DeveloperRepository;
import com.vymo.bandviz.repository.JiraTicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BandwidthServiceTest {

    @Mock
    private DeveloperRepository developerRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private LeaveService leaveService;

    @Mock
    private JiraTicketRepository jiraTicketRepository;

    @Mock
    private PlanningWindowService planningWindowService;

    @InjectMocks
    private BandwidthService bandwidthService;

    @Test
    void computeForPeriod_shouldReturnOverloadedAndOnLeaveStatusesWithTicketStats() {
        LocalDate start = LocalDate.of(2026, 3, 17);
        LocalDate end = LocalDate.of(2026, 3, 28);

        Developer d1 = Developer.builder()
                .id(1L)
                .name("Arjun")
                .role(DeveloperRole.SENIOR_BACKEND_ENGINEER)
                .jiraUsername("arjun@vymo.com")
                .weeklyCapacityHours(40)
                .active(true)
                .build();

        Developer d2 = Developer.builder()
                .id(2L)
                .name("Siddharth")
                .role(DeveloperRole.FULL_STACK_ENGINEER)
                .jiraUsername("sid@vymo.com")
                .weeklyCapacityHours(40)
                .active(true)
                .build();

        Project p1 = Project.builder().id(10L).name("CRM Core").color("#6366f1").build();
        Project p2 = Project.builder().id(11L).name("Analytics").color("#a855f7").build();

        Assignment a1 = Assignment.builder().developer(d1).project(p1).allocationPct(50).build();
        Assignment a2 = Assignment.builder().developer(d1).project(p2).allocationPct(45).build();

        JiraTicket t1 = JiraTicket.builder().ticketKey("CRM-1").status(TicketStatus.IN_PROGRESS).storyPoints(8).build();
        JiraTicket t2 = JiraTicket.builder().ticketKey("CRM-2").status(TicketStatus.BLOCKED).storyPoints(5).build();
        t1.setAssigneeJiraUsername("arjun@vymo.com");
        t2.setAssigneeJiraUsername("arjun@vymo.com");

        when(developerRepository.findAllByActiveTrue()).thenReturn(List.of(d1, d2));
        when(leaveService.countWorkingDays(start, end)).thenReturn(10L);
        when(assignmentRepository.findAllActiveInRange(start, end)).thenReturn(List.of(a1, a2));
        when(leaveService.countApprovedLeaveDays(1L, start, end)).thenReturn(0L);
        when(leaveService.countApprovedLeaveDays(2L, start, end)).thenReturn(10L);
        when(jiraTicketRepository.findAllByAssigneeJiraUsernameInAndStatusNot(List.of("arjun@vymo.com", "sid@vymo.com"), TicketStatus.DONE))
                .thenReturn(List.of(t1, t2));

        List<DeveloperBandwidthResponse> result = bandwidthService.computeForPeriod(start, end);

        assertEquals(2, result.size());

        DeveloperBandwidthResponse arjun = result.stream().filter(r -> r.getDeveloperId().equals(1L)).findFirst().orElseThrow();
        assertEquals(95, arjun.getTotalAllocationPct());
        assertEquals(BandwidthStatus.OVERLOADED, arjun.getStatus());
        assertEquals(2L, arjun.getOpenTickets());
        assertEquals(1L, arjun.getBlockedTickets());
        assertEquals(13, arjun.getTotalStoryPoints());

        DeveloperBandwidthResponse siddharth = result.stream().filter(r -> r.getDeveloperId().equals(2L)).findFirst().orElseThrow();
        assertEquals(BandwidthStatus.ON_LEAVE, siddharth.getStatus());
        assertEquals(0.0, siddharth.getEffectiveBandwidthPct());
    }

    @Test
    void computeForPeriod_shouldQueryAssignmentsOverlappingTheWholeRange() {
        LocalDate start = LocalDate.of(2026, 3, 28);
        LocalDate end = LocalDate.of(2026, 4, 6);

        Developer d = Developer.builder()
                .id(1L)
                .name("Arjun")
                .role(DeveloperRole.BACKEND_ENGINEER)
                .weeklyCapacityHours(40)
                .active(true)
                .build();

        when(developerRepository.findAllByActiveTrue()).thenReturn(List.of(d));
        when(leaveService.countWorkingDays(start, end)).thenReturn(6L);
        when(assignmentRepository.findAllActiveInRange(start, end)).thenReturn(List.of());
        when(jiraTicketRepository.findAllByAssigneeJiraUsernameInAndStatusNot(List.of(), TicketStatus.DONE)).thenReturn(List.of());
        when(leaveService.countApprovedLeaveDays(1L, start, end)).thenReturn(0L);

        bandwidthService.computeForPeriod(start, end);

        verify(assignmentRepository).findAllActiveInRange(start, end);
        assertTrue(true);
    }

    @Test
    void computeForActiveWindow_shouldUseResolvedPlanningWindow() {
        LocalDate start = LocalDate.of(2026, 3, 28);
        LocalDate end = LocalDate.of(2026, 4, 10);

        Developer d = Developer.builder()
                .id(1L)
                .name("Akshath")
                .role(DeveloperRole.ENGINEERING_MANAGER)
                .weeklyCapacityHours(40)
                .active(true)
                .build();

        when(planningWindowService.resolveCurrentWindow())
                .thenReturn(new PlanningWindow("Current Planning Window", start, end, "KANBAN", true));
        when(developerRepository.findAllByActiveTrue()).thenReturn(List.of(d));
        when(leaveService.countWorkingDays(start, end)).thenReturn(10L);
        when(assignmentRepository.findAllActiveInRange(start, end)).thenReturn(List.of());
        when(jiraTicketRepository.findAllByAssigneeJiraUsernameInAndStatusNot(List.of(), TicketStatus.DONE)).thenReturn(List.of());
        when(leaveService.countApprovedLeaveDays(1L, start, end)).thenReturn(0L);

        List<DeveloperBandwidthResponse> result = bandwidthService.computeForActiveWindow();

        assertEquals(1, result.size());
        assertEquals(BandwidthStatus.AVAILABLE, result.get(0).getStatus());
        verify(planningWindowService).resolveCurrentWindow();
    }
}
