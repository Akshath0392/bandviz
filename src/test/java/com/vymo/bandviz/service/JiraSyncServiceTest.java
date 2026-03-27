package com.vymo.bandviz.service;

import com.vymo.bandviz.config.JiraProperties;
import com.vymo.bandviz.domain.JiraTicket;
import com.vymo.bandviz.domain.Project;
import com.vymo.bandviz.domain.enums.ProjectDeliveryMode;
import com.vymo.bandviz.domain.enums.TicketPriority;
import com.vymo.bandviz.domain.enums.TicketStatus;
import com.vymo.bandviz.integration.JiraClient;
import com.vymo.bandviz.repository.JiraTicketRepository;
import com.vymo.bandviz.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JiraSyncServiceTest {

    @Mock
    private JiraClient jiraClient;

    @Mock
    private JiraTicketRepository jiraTicketRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Spy
    private JiraProperties jiraProperties = new JiraProperties();

    @InjectMocks
    private JiraSyncService jiraSyncService;

    @Test
    void getStatus_shouldExposeConfiguredBaseUrlAndProjectKeys() {
        jiraProperties.setSyncEnabled(true);
        jiraProperties.setBaseUrl("https://teamvymo.atlassian.net");
        jiraProperties.setProjectKeys(List.of("FICC", "COLLXPD"));

        when(jiraTicketRepository.count()).thenReturn(57L);

        JiraSyncService.SyncStatusResponse response = jiraSyncService.getStatus();

        assertTrue(response.syncEnabled());
        assertEquals("https://teamvymo.atlassian.net", response.baseUrl());
        assertEquals(List.of("FICC", "COLLXPD"), response.projectKeys());
        assertEquals(57L, response.totalTickets());
        assertEquals("Never synced", response.lastSyncStatus());
    }

    @Test
    void getProjectMappings_shouldSummarizeOpenTicketsAndFreshness() {
        Project freshProject = Project.builder()
                .id(1L)
                .name("Fullerton")
                .jiraProjectKey("FICC")
                .deliveryMode(ProjectDeliveryMode.HYBRID)
                .active(true)
                .build();
        Project staleProject = Project.builder()
                .id(2L)
                .name("Collections Productisation POD")
                .jiraProjectKey("COLLXPD")
                .deliveryMode(ProjectDeliveryMode.KANBAN)
                .active(true)
                .build();
        Project unsyncedProject = Project.builder()
                .id(3L)
                .name("Roadmap")
                .jiraProjectKey("ROADMAP")
                .deliveryMode(ProjectDeliveryMode.SPRINT)
                .active(true)
                .build();

        LocalDateTime freshSync = LocalDateTime.now().minusMinutes(10);
        LocalDateTime staleSync = LocalDateTime.now().minusHours(2);

        JiraTicket freshTicket = JiraTicket.builder()
                .ticketKey("FICC-12")
                .projectKey("FICC")
                .status(TicketStatus.IN_PROGRESS)
                .priority(TicketPriority.HIGH)
                .lastSyncedAt(freshSync)
                .build();
        JiraTicket staleTicket = JiraTicket.builder()
                .ticketKey("COLLXPD-3")
                .projectKey("COLLXPD")
                .status(TicketStatus.TO_DO)
                .priority(TicketPriority.MEDIUM)
                .lastSyncedAt(staleSync)
                .build();

        when(projectRepository.findAllByActiveTrue()).thenReturn(List.of(freshProject, staleProject, unsyncedProject));
        when(jiraTicketRepository.findAllByProjectKey("FICC")).thenReturn(List.of(freshTicket));
        when(jiraTicketRepository.findAllByProjectKey("COLLXPD")).thenReturn(List.of(staleTicket));
        when(jiraTicketRepository.findAllByProjectKey("ROADMAP")).thenReturn(List.of());
        when(jiraTicketRepository.countByProjectKeyAndStatusNot("FICC", TicketStatus.DONE)).thenReturn(8L);
        when(jiraTicketRepository.countByProjectKeyAndStatusNot("COLLXPD", TicketStatus.DONE)).thenReturn(3L);
        when(jiraTicketRepository.countByProjectKeyAndStatusNot("ROADMAP", TicketStatus.DONE)).thenReturn(0L);

        List<JiraSyncService.ProjectSyncStatusResponse> response = jiraSyncService.getProjectMappings();

        assertEquals(3, response.size());

        JiraSyncService.ProjectSyncStatusResponse fresh = response.get(0);
        assertEquals("Fullerton", fresh.projectName());
        assertEquals("FICC", fresh.jiraProjectKey());
        assertEquals(8L, fresh.openTickets());
        assertEquals("SYNCED", fresh.syncState());
        assertNotNull(fresh.lastSyncedAt());

        JiraSyncService.ProjectSyncStatusResponse stale = response.get(1);
        assertEquals("COLLXPD", stale.jiraProjectKey());
        assertEquals("STALE", stale.syncState());
        assertEquals(3L, stale.openTickets());

        JiraSyncService.ProjectSyncStatusResponse unsynced = response.get(2);
        assertEquals("ROADMAP", unsynced.jiraProjectKey());
        assertEquals("NOT_SYNCED", unsynced.syncState());
        assertEquals(0L, unsynced.openTickets());
    }
}
