package com.vymo.bandviz.service;

import com.vymo.bandviz.domain.enums.BandwidthStatus;
import com.vymo.bandviz.domain.enums.DeveloperRole;
import com.vymo.bandviz.domain.enums.TicketStatus;
import com.vymo.bandviz.dto.response.DashboardResponse;
import com.vymo.bandviz.dto.response.DeveloperBandwidthResponse;
import com.vymo.bandviz.repository.JiraTicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private BandwidthService bandwidthService;

    @Mock
    private JiraTicketRepository jiraTicketRepository;

    @Mock
    private PlanningWindowService planningWindowService;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void getActiveSprint_shouldUseFallbackPlanningWindowMetadata() {
        PlanningWindow window = new PlanningWindow(
                "Current Planning Window",
                LocalDate.of(2026, 3, 28),
                LocalDate.of(2026, 4, 10),
                "KANBAN",
                true
        );

        DeveloperBandwidthResponse available = DeveloperBandwidthResponse.builder()
                .developerId(1L)
                .developerName("Suhan K")
                .role(DeveloperRole.TECH_LEAD)
                .effectiveBandwidthPct(45.0)
                .status(BandwidthStatus.AVAILABLE)
                .openTickets(2L)
                .blockedTickets(1L)
                .build();

        DeveloperBandwidthResponse onLeave = DeveloperBandwidthResponse.builder()
                .developerId(2L)
                .developerName("Akshath T A")
                .role(DeveloperRole.ENGINEERING_MANAGER)
                .effectiveBandwidthPct(0.0)
                .status(BandwidthStatus.ON_LEAVE)
                .openTickets(3L)
                .blockedTickets(0L)
                .build();

        when(planningWindowService.resolveCurrentWindow()).thenReturn(window);
        when(bandwidthService.computeForActiveWindow()).thenReturn(List.of(available, onLeave));
        when(jiraTicketRepository.countByStatus(TicketStatus.IN_PROGRESS)).thenReturn(3L);
        when(jiraTicketRepository.countByStatus(TicketStatus.TO_DO)).thenReturn(4L);
        when(jiraTicketRepository.countByStatus(TicketStatus.IN_REVIEW)).thenReturn(2L);
        when(jiraTicketRepository.countByStatus(TicketStatus.BLOCKED)).thenReturn(1L);
        when(jiraTicketRepository.countByStatusAndLastSyncedAtGreaterThanEqual(
                TicketStatus.DONE,
                window.startDate().atStartOfDay()
        )).thenReturn(7L);

        DashboardResponse response = dashboardService.getActiveSprint();

        assertEquals("Current Planning Window", response.getSprintName());
        assertEquals("KANBAN", response.getPlanningMode());
        assertTrue(response.getUsingFallbackWindow());
        assertEquals(2, response.getTotalDevelopers());
        assertEquals(1, response.getAvailableCount());
        assertEquals(1, response.getOnLeaveCount());
        assertEquals(10L, response.getTotalOpenTickets());
        assertEquals(1L, response.getTotalBlockedTickets());
        assertEquals(7L, response.getTotalClosedInWindow());
        assertEquals(7L, response.getTotalClosedThisSprint());
        assertFalse(response.getAlerts().isEmpty());
    }

    @Test
    void getForSprint_shouldPreserveExplicitSprintWindow() {
        PlanningWindow window = new PlanningWindow(
                "Sprint 42",
                LocalDate.of(2026, 3, 17),
                LocalDate.of(2026, 3, 28),
                "SPRINT",
                false
        );

        when(planningWindowService.resolveSprintWindow(42L)).thenReturn(window);
        when(bandwidthService.computeForSprint(42L)).thenReturn(List.of());
        when(jiraTicketRepository.countByStatus(TicketStatus.IN_PROGRESS)).thenReturn(0L);
        when(jiraTicketRepository.countByStatus(TicketStatus.TO_DO)).thenReturn(0L);
        when(jiraTicketRepository.countByStatus(TicketStatus.IN_REVIEW)).thenReturn(0L);
        when(jiraTicketRepository.countByStatus(TicketStatus.BLOCKED)).thenReturn(0L);
        when(jiraTicketRepository.countByStatusAndLastSyncedAtGreaterThanEqual(
                eq(TicketStatus.DONE),
                any()
        )).thenReturn(0L);

        DashboardResponse response = dashboardService.getForSprint(42L);

        assertEquals("Sprint 42", response.getSprintName());
        assertEquals("SPRINT", response.getPlanningMode());
        assertFalse(response.getUsingFallbackWindow());
        assertEquals(0, response.getTotalDevelopers());
    }
}
